package com.orangemall.seckill.service;

import com.orangemall.common.BizException;
import com.orangemall.common.Const;
import com.orangemall.seckill.config.SeckillProps;
import com.orangemall.seckill.entity.SeckillDeadLetter;
import com.orangemall.seckill.mapper.SeckillActivityMapper;
import com.orangemall.seckill.mapper.SeckillDeadLetterMapper;
import com.orangemall.seckill.mq.SeckillOrderProcessor;
import com.orangemall.seckill.support.KafkaLagProbe;
import com.orangemall.seckill.support.Keys;
import com.orangemall.seckill.support.SeckillCalendar;
import com.orangemall.web.OpsLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.LongSupplier;

/**
 * 秒杀可靠性（OM-013 在途滞留 / OM-019 死信）：
 * <ul>
 *   <li>在途巡检：在途数 &gt; 0 且持续 {@code inflight-stuck-seconds} 未变化 → 判定滞留。
 *       主题仍有积压 → 只告警（消费者停滞/暂停/宕机，恢复后自然归零）；
 *       无积压 → 判定计数泄漏（消息丢失或补偿失败），按配置自动重置在途并以 DB 为准回补 Redis 库存。</li>
 *   <li>手动重置、健康视图、死信列表/标记处理、主消费者暂停/恢复（故障演练）。</li>
 * </ul>
 * 重置只扣掉「判定时观察到的滞留值」，判定之后新进入的在途保留（inflight_reset.lua）；
 * 即便与新请求并发导致 Redis 库存短暂偏高，DB 乐观锁 {@code WHERE stock > 0} 仍保证不超卖。
 */
@Slf4j
@Service
public class SeckillReliabilityService {
    static final String ST_NEW = "NEW";
    static final String ST_BACKLOG = "BACKLOG";
    static final String ST_UNKNOWN = "UNKNOWN";
    static final String ST_LEAK = "LEAK";

    private final StringRedisTemplate redis;
    private final SeckillActivityMapper activityMapper;
    private final SeckillDeadLetterMapper deadLetters;
    private final KafkaLagProbe lagProbe;
    private final KafkaListenerEndpointRegistry registry;
    private final SeckillProps props;
    private final OpsLogger ops;
    private final DefaultRedisScript<List> resetScript;

    public SeckillReliabilityService(StringRedisTemplate redis, SeckillActivityMapper activityMapper,
                                     SeckillDeadLetterMapper deadLetters, KafkaLagProbe lagProbe,
                                     KafkaListenerEndpointRegistry registry, SeckillProps props, OpsLogger ops,
                                     @Qualifier("inflightResetScript") DefaultRedisScript<List> resetScript) {
        this.redis = redis;
        this.activityMapper = activityMapper;
        this.deadLetters = deadLetters;
        this.lagProbe = lagProbe;
        this.registry = registry;
        this.props = props;
        this.ops = ops;
        this.resetScript = resetScript;
    }

    /* ============================ OM-013 在途巡检 ============================ */

    /**
     * 由 SeckillJob 每轮对窗口内每个活动调用。{@code lag} 为惰性探测（一轮最多探测一次 Kafka）。
     * watch 记录：{@code seckill:inflight:watch} Hash，field = activityId，value = "在途|起始毫秒|状态"。
     */
    public void watchInflight(String aid, int inflight, long now, LongSupplier lag) {
        String raw = (String) redis.opsForHash().get(Keys.INFLIGHT_WATCH, aid);
        Watch w = Watch.parse(raw);
        if (inflight <= 0) {
            if (w != null) {
                if (!ST_NEW.equals(w.state)) {
                    ops.log("ALERT", "[在途恢复] " + aid + " 在途已归零（此前状态 " + w.state + "，持续 "
                            + (now - w.since) / 1000 + "s）");
                }
                redis.opsForHash().delete(Keys.INFLIGHT_WATCH, aid);
            }
            return;
        }
        if (w == null || w.value != inflight) {
            put(aid, new Watch(inflight, now, ST_NEW));
            return;
        }
        long held = (now - w.since) / 1000;
        if (held < props.getInflightStuckSeconds()) return;

        long l = lag.getAsLong();
        if (l > 0) {
            if (!ST_BACKLOG.equals(w.state)) {
                ops.log("ALERT", "[在途滞留] " + aid + " 在途 " + inflight + " 持续 " + held + "s 未变化，主题积压 " + l
                        + " 条：消费者可能停滞 / 被暂停 / 宕机（恢复后自动归零，不做重置）");
                put(aid, new Watch(inflight, w.since, ST_BACKLOG));
            }
            return;
        }
        if (l < 0) {
            if (!ST_UNKNOWN.equals(w.state)) {
                ops.log("ALERT", "[在途滞留] " + aid + " 在途 " + inflight + " 持续 " + held
                        + "s 未变化，Kafka 积压探测失败，暂不自动处理（可在后台手动强制重置）");
                put(aid, new Watch(inflight, w.since, ST_UNKNOWN));
            }
            return;
        }
        // 无积压却仍有在途：计数泄漏（消息丢失 / 补偿失败）
        if (props.isInflightAutoReset()) {
            ops.log("ALERT", "[在途泄漏] " + aid + " 在途 " + inflight + " 持续 " + held + "s 且无待消费消息 → 判定计数泄漏，自动重置");
            reset(aid, inflight, "AUTO");
        } else if (!ST_LEAK.equals(w.state)) {
            ops.log("ALERT", "[在途泄漏] " + aid + " 在途 " + inflight + " 持续 " + held + "s 且无待消费消息 → 判定计数泄漏（自动重置已关闭，请手动处理）");
            put(aid, new Watch(inflight, w.since, ST_LEAK));
        }
    }

    /** 后台手动重置：有积压（或探测失败）时必须显式 force，避免把真实排队中的在途清掉 */
    public Map<String, Object> manualReset(String aid, boolean force, long adminId) {
        if (activityMapper.selectById(aid) == null) throw new BizException(7001, "活动不存在");
        String infRaw = redis.opsForValue().get(Keys.inflight(aid));
        int inflight = infRaw == null ? 0 : Integer.parseInt(infRaw);
        if (inflight <= 0) return Map.of("reset", false, "inflight", 0);
        long l = lagProbe.lag(Const.KAFKA_SECKILL_TOPIC);
        if (l != 0 && !force) {
            throw new BizException(7010, l > 0
                    ? "主题仍有 " + l + " 条未消费消息，在途可能是正常排队；确认要强制重置请勾选「强制」"
                    : "Kafka 积压探测失败，无法确认在途是否泄漏；确认要强制重置请勾选「强制」");
        }
        return reset(aid, inflight, "admin:" + adminId + (force ? "(force)" : ""));
    }

    private Map<String, Object> reset(String aid, int observed, String by) {
        Integer db = activityMapper.stockOf(aid);
        if (db == null) throw new BizException(7001, "活动不存在");
        @SuppressWarnings("unchecked")
        List<Long> r = redis.execute(resetScript, List.of(Keys.stock(aid), Keys.inflight(aid)),
                String.valueOf(db), String.valueOf(observed));
        long leftInflight = r == null ? 0 : r.get(0);
        long stock = r == null ? -1 : r.get(1);
        redis.opsForHash().delete(Keys.INFLIGHT_WATCH, aid);
        redis.opsForHash().increment(Keys.METRICS, "inflightResets", 1);
        ops.log("ALERT", "[在途重置] " + aid + " 由 " + by + " 扣除滞留在途 " + observed + " → 剩余在途 " + leftInflight
                + (stock < 0 ? "（未预热，未改库存）" : "，Redis 库存以 DB 为准回补为 " + stock + "（DB=" + db + "）"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reset", true);
        out.put("cleared", observed);
        out.put("inflight", leftInflight);
        out.put("redisStock", stock);
        out.put("dbStock", db);
        return out;
    }

    /* ============================ 健康视图 ============================ */

    public Map<String, Object> health() {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SeckillCalendar.Session s : SeckillCalendar.window(now)) {
            for (SeckillCalendar.Tpl tpl : SeckillCalendar.rotated(s.start())) {
                String aid = SeckillCalendar.activityId(s.id(), tpl.skuId());
                String infRaw = redis.opsForValue().get(Keys.inflight(aid));
                int inflight = infRaw == null ? 0 : Integer.parseInt(infRaw);
                Watch w = Watch.parse((String) redis.opsForHash().get(Keys.INFLIGHT_WATCH, aid));
                if (inflight <= 0 && w == null) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("activityId", aid);
                row.put("session", s.label());
                row.put("inflight", inflight);
                row.put("heldSeconds", w == null ? 0 : (now - w.since) / 1000);
                row.put("state", w == null ? ST_NEW : w.state);
                rows.add(row);
            }
        }
        MessageListenerContainer c = registry.getListenerContainer(SeckillOrderProcessor.LISTENER_ID);
        Map<String, Object> consumer = new LinkedHashMap<>();
        consumer.put("running", c != null && c.isRunning());
        consumer.put("paused", c != null && (c.isPauseRequested() || c.isContainerPaused()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("consumer", consumer);
        out.put("lag", lagProbe.lag(Const.KAFKA_SECKILL_TOPIC));
        out.put("stuckSeconds", props.getInflightStuckSeconds());
        out.put("autoReset", props.isInflightAutoReset());
        out.put("inflight", rows);
        out.put("deadLetters", Map.of("new", deadLetters.countNew(), "total", deadLetters.countAll()));
        return out;
    }

    /* ============================ OM-019 死信 ============================ */

    public List<SeckillDeadLetter> deadLetters(String status) {
        return deadLetters.recent(status == null || status.isBlank() ? null : status, 100);
    }

    public boolean resolve(long id, String remark, long adminId) {
        if (deadLetters.resolve(id, remark == null ? "" : remark, adminId) == 0) {
            throw new BizException(7011, "死信不存在或已处理");
        }
        ops.log("SECKILL", "[死信] #" + id + " 已由 admin:" + adminId + " 标记处理" + (remark == null || remark.isBlank() ? "" : "：" + remark));
        return true;
    }

    /* ============================ 故障演练：暂停/恢复主消费者 ============================ */

    public Map<String, Object> consumer(boolean pause, long adminId) {
        MessageListenerContainer c = registry.getListenerContainer(SeckillOrderProcessor.LISTENER_ID);
        if (c == null) throw new BizException(7012, "秒杀消费者未就绪");
        if (pause) c.pause();
        else c.resume();
        ops.log("ALERT", "[演练] admin:" + adminId + (pause ? " 暂停" : " 恢复") + "秒杀主消费者");
        return Map.of("paused", pause);
    }

    private void put(String aid, Watch w) {
        redis.opsForHash().put(Keys.INFLIGHT_WATCH, aid, w.value + "|" + w.since + "|" + w.state);
    }

    record Watch(int value, long since, String state) {
        static Watch parse(String raw) {
            if (raw == null) return null;
            String[] p = raw.split("\\|");
            if (p.length != 3) return null;
            try {
                return new Watch(Integer.parseInt(p[0]), Long.parseLong(p[1]), p[2]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
