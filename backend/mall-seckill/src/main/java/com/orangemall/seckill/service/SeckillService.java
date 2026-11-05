package com.orangemall.seckill.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangemall.api.client.ProductInnerClient;
import com.orangemall.api.client.UserInnerClient;
import com.orangemall.api.dto.SkuInfo;
import com.orangemall.common.BizException;
import com.orangemall.common.Const;
import com.orangemall.common.IdGenerator;
import com.orangemall.seckill.config.SeckillProps;
import com.orangemall.seckill.entity.SeckillActivity;
import com.orangemall.seckill.mapper.SeckillActivityMapper;
import com.orangemall.seckill.mapper.SeckillOrderMapper;
import com.orangemall.seckill.support.Keys;
import com.orangemall.seckill.support.SeckillCalendar;
import com.orangemall.web.OpsLogger;
import com.orangemall.web.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * 秒杀核心：四层防护（令牌桶 → path/风控 → Lua 判重扣减 → Kafka 异步落库）。
 * 活动行由 SeckillJob 滚动建场并自动预热；Redis 库存与 DB 库存以「在途数」对账。
 */
@Slf4j
@Service
public class SeckillService {
    private final SeckillActivityMapper activityMapper;
    private final SeckillOrderMapper orderMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper om;
    private final SeckillProps props;
    private final ProductInnerClient product;
    private final UserInnerClient userClient;
    private final KafkaTemplate<Object, Object> kafka;
    private final OpsLogger ops;
    private final DefaultRedisScript<Long> guardScript;
    private final DefaultRedisScript<Long> stockScript;

    public record SeckillItemVO(String activityId, String sessionId, String skuId, String spuId, String name,
                                String art, String tint, int seckillPrice, int originalPrice, int totalStock,
                                int remainStock, int limitPerUser, String startAt, String endAt, String status) {
    }

    public record SessionVO(String id, String label, String startAt, String endAt, String status,
                            List<SeckillItemVO> items) {
    }

    public SeckillService(SeckillActivityMapper activityMapper, SeckillOrderMapper orderMapper,
                          StringRedisTemplate redis, ObjectMapper om, SeckillProps props,
                          ProductInnerClient product, UserInnerClient userClient,
                          KafkaTemplate<Object, Object> kafka, OpsLogger ops,
                          DefaultRedisScript<Long> guardScript, DefaultRedisScript<Long> stockScript) {
        this.activityMapper = activityMapper;
        this.orderMapper = orderMapper;
        this.redis = redis;
        this.om = om;
        this.props = props;
        this.product = product;
        this.userClient = userClient;
        this.kafka = kafka;
        this.ops = ops;
        this.guardScript = guardScript;
        this.stockScript = stockScript;
    }

    /* ============================ 查询 ============================ */
    public List<SessionVO> sessions() {
        long now = System.currentTimeMillis();
        // 一次批量查活动行：sessions/item/preview 若逐行 selectById，单请求会打 32 次串行 DB
        //（压测实锤：20 连接池被瞬间排空，QPS 卡在 ~130）。先收集窗口内全部 activityId 再 selectBatchIds。
        List<SeckillCalendar.Session> window = SeckillCalendar.window(now);
        if (window.isEmpty()) return List.of();
        List<String> allIds = new ArrayList<>();
        for (SeckillCalendar.Session s : window) {
            for (SeckillCalendar.Tpl tpl : SeckillCalendar.rotated(s.start())) allIds.add(SeckillCalendar.activityId(s.id(), tpl.skuId()));
        }
        Map<String, SeckillActivity> rows = rows(allIds);
        List<SessionVO> out = new ArrayList<>();
        for (SeckillCalendar.Session s : window) {
            List<SeckillItemVO> items = new ArrayList<>();
            for (SeckillCalendar.Tpl tpl : SeckillCalendar.rotated(s.start())) {
                String aid = SeckillCalendar.activityId(s.id(), tpl.skuId());
                items.add(toItem(aid, s, tpl, rows.get(aid), now));
            }
            out.add(new SessionVO(s.id(), s.label(), s.startIso(), s.endIso(), s.status(now), items));
        }
        return out;
    }

    public SeckillItemVO item(String activityId) {
        long now = System.currentTimeMillis();
        for (SeckillCalendar.Session s : SeckillCalendar.window(now)) {
            for (SeckillCalendar.Tpl tpl : SeckillCalendar.rotated(s.start())) {
                if (SeckillCalendar.activityId(s.id(), tpl.skuId()).equals(activityId)) {
                    return toItem(activityId, s, tpl, row(activityId), now);
                }
            }
        }
        throw new BizException(7001, "秒杀活动不存在");
    }

    private SeckillItemVO toItem(String aid, SeckillCalendar.Session s, SeckillCalendar.Tpl tpl,
                                 SeckillActivity row, long now) {
        String status = s.status(now);
        String stockRaw = redis.opsForValue().get(Keys.stock(aid));
        boolean warmed = stockRaw != null;
        int remain = warmed ? Math.max(0, Integer.parseInt(stockRaw)) : tpl.stock();
        return new SeckillItemVO(aid, s.id(), String.valueOf(tpl.skuId()), String.valueOf(tpl.spuId()),
                row != null && row.getName() != null ? row.getName() : "热销爆品",
                row != null && row.getArt() != null ? row.getArt() : "phone",
                row != null && row.getTint() != null ? row.getTint() : "#ff5a00",
                tpl.price(), row != null && row.getOriginalPrice() != null ? row.getOriginalPrice() : tpl.price(),
                tpl.stock(), remain, 1, s.startIso(), s.endIso(), status);
    }

    /** 活动行展示信息：DB 建场后 name/art/tint 以 DB 为准，未建场用模板兜底 */
    private SeckillActivity row(String aid) {
        Map<String, SeckillActivity> m = rows(List.of(aid));
        return m.get(aid);
    }

    /** 批量取活动行（单次 selectBatchIds，避免逐行查询） */
    private Map<String, SeckillActivity> rows(List<String> aids) {
        if (aids.isEmpty()) return Map.of();
        Map<String, SeckillActivity> m = new HashMap<>();
        for (SeckillActivity a : activityMapper.selectBatchIds(aids)) m.put(a.getId(), a);
        return m;
    }

    /** 首页秒杀预览（进行中场次前 4 个） */
    public Map<String, Object> preview() {
        long now = System.currentTimeMillis();
        for (SeckillCalendar.Session s : SeckillCalendar.window(now)) {
            if ("ONGOING".equals(s.status(now))) {
                List<SeckillCalendar.Tpl> tpls = SeckillCalendar.rotated(s.start());
                List<String> ids = new ArrayList<>();
                for (SeckillCalendar.Tpl tpl : tpls) ids.add(SeckillCalendar.activityId(s.id(), tpl.skuId()));
                Map<String, SeckillActivity> rows = rows(ids);
                List<Map<String, Object>> items = new ArrayList<>();
                int i = 0;
                for (SeckillCalendar.Tpl tpl : tpls) {
                    if (i++ >= 4) break;
                    SeckillItemVO it = toItem(SeckillCalendar.activityId(s.id(), tpl.skuId()), s, tpl,
                            rows.get(SeckillCalendar.activityId(s.id(), tpl.skuId())), now);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("activityId", it.activityId());
                    m.put("sessionId", it.sessionId());
                    m.put("skuId", it.skuId());
                    m.put("spuId", it.spuId());
                    m.put("name", it.name());
                    m.put("art", it.art());
                    m.put("tint", it.tint());
                    m.put("seckillPrice", it.seckillPrice());
                    m.put("originalPrice", it.originalPrice());
                    m.put("totalStock", it.totalStock());
                    m.put("remainStock", it.remainStock());
                    m.put("limitPerUser", it.limitPerUser());
                    m.put("startAt", it.startAt());
                    m.put("endAt", it.endAt());
                    m.put("status", it.status());
                    items.add(m);
                }
                return Map.of("endAt", s.endIso(), "items", items);
            }
        }
        return Map.of("endAt", null, "items", List.of());
    }

    public boolean bought(String activityId, long userId) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(Keys.bought(activityId), String.valueOf(userId)))
                || orderMapper.find(activityId, userId) != null;
    }

    /* ============================ 下单四层 ============================ */
    public String issuePath(String activityId) {
        SeckillItemVO it = item(activityId);
        long now = System.currentTimeMillis();
        if (!"ONGOING".equals(it.status())) throw new BizException(7002, "UPCOMING".equals(it.status()) ? "活动未开始" : "活动已结束");
        String path = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(Keys.path(activityId, UserContext.requireUser()), path,
                java.time.Duration.ofSeconds(props.getPathTtlSeconds()));
        return path;
    }

    /** @return {state:'QUEUING', orderId} */
    public Map<String, Object> submit(String activityId, String path, Long addressId) {
        long userId = UserContext.requireUser();
        long now = System.currentTimeMillis();
        SeckillItemVO it = item(activityId);
        SeckillActivity row = activityMapper.selectById(activityId);
        if (row == null) throw new BizException(7001, "秒杀活动不存在");
        if (!"ONGOING".equals(it.status())) throw new BizException(7002, "活动未开始或已结束");

        // 第 1、2 层：令牌桶 + path + 风控（一次 Redis 往返）
        String pathKey = Keys.path(activityId, userId);
        Long guard = redis.execute(guardScript,
                List.of(Keys.bucket(userId), Keys.METRICS, pathKey, Const.K_RISK_CREDIT, Const.K_RISK_BLOCKED),
                String.valueOf(now), String.valueOf(props.getBucketCapacity()), String.valueOf(props.getBucketRate()),
                path, String.valueOf(userId), String.valueOf(props.getMinCredit()));
        if (guard == null) guard = 0L;
        if (guard == 1) throw new BizException(4290, "当前抢购人数过多，请稍后再试");
        if (guard == 2) throw new BizException(7003, "秒杀地址无效或已过期");
        if (guard == 3) throw new BizException(7009, "账户存在异常行为，暂不能参与秒杀");

        // 第 3 层：Lua 判重 + 扣减 + 在途计数
        String resultKey = Keys.result(activityId, userId);
        String queuing = json("state", "QUEUING");
        Long left = redis.execute(stockScript,
                List.of(Keys.stock(activityId), Keys.bought(activityId), Keys.inflight(activityId),
                        Keys.METRICS, resultKey, pathKey),
                String.valueOf(userId), queuing, String.valueOf(props.getResultTtlSeconds()));
        if (left == null || left == -2) throw new BizException(7005, "已抢完，下次早点来哦");
        if (left == -1) throw new BizException(7004, "每人限购 1 件，您已抢到过啦");
        if (left < 0) throw new BizException(7005, "已抢完，下次早点来哦");

        // 第 4 层：投递 Kafka，立即返回排队中
        long orderId = IdGenerator.nextId(userId & 3);
        ops.log("SECKILL", "[Lua] " + activityId + " 扣减成功 剩余 " + left + " → Kafka topic seckill-order 投递 orderId=" + orderId);
        try {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("orderId", orderId);
            msg.put("userId", userId);
            msg.put("activityId", activityId);
            msg.put("skuId", row.getSkuId());
            msg.put("seckillPrice", it.seckillPrice());
            msg.put("addressId", addressId);
            msg.put("sim", false);
            kafka.send(Const.KAFKA_SECKILL_TOPIC, activityId, om.writeValueAsString(msg));
        } catch (Exception e) {
            log.error("kafka send failed: {}", e.getMessage());
            compensate(activityId, userId, "系统繁忙，请重试", false);
            throw new BizException(500, "系统繁忙，请重试");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("state", "QUEUING");
        out.put("orderId", String.valueOf(orderId));
        return out;
    }

    void compensate(String activityId, long userId, String reason, boolean sim) {
        String result = json2("FAILED", null, reason);
        redis.execute(finishScript(), List.of(Keys.stock(activityId), Keys.bought(activityId),
                        Keys.inflight(activityId), Keys.METRICS, Keys.result(activityId, userId)),
                String.valueOf(userId), result, String.valueOf(props.getResultTtlSeconds()), "1", "1", "");
    }

    private DefaultRedisScript<Long> finishScript;

    private DefaultRedisScript<Long> finishScript() {
        if (finishScript == null) {
            finishScript = new DefaultRedisScript<>();
            finishScript.setLocation(new org.springframework.core.io.ClassPathResource("lua/finish.lua"));
            finishScript.setResultType(Long.class);
        }
        return finishScript;
    }

    public JsonNode result(String activityId, long userId) {
        String raw = redis.opsForValue().get(Keys.result(activityId, userId));
        try {
            return raw == null ? om.readTree("{\"state\":\"FAILED\",\"reason\":\"未查询到抢购记录\"}") : om.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    String json(String k, String v) {
        try {
            return om.writeValueAsString(Map.of(k, v));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    String json2(String state, String orderId, String reason) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("state", state);
            if (orderId != null) m.put("orderId", orderId);
            if (reason != null) m.put("reason", reason);
            return om.writeValueAsString(m);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /* ============================ 后台 ============================ */
    public Map<String, Object> adminView() {
        List<Map<String, Object>> sessions = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (SessionVO s : sessions()) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (SeckillItemVO it : s.items()) {
                Integer dbStock = activityMapper.stockOf(it.activityId());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("activityId", it.activityId());
                m.put("sessionId", it.sessionId());
                m.put("skuId", it.skuId());
                m.put("spuId", it.spuId());
                m.put("name", it.name());
                m.put("art", it.art());
                m.put("tint", it.tint());
                m.put("seckillPrice", it.seckillPrice());
                m.put("originalPrice", it.originalPrice());
                m.put("totalStock", it.totalStock());
                m.put("remainStock", it.remainStock());
                m.put("limitPerUser", it.limitPerUser());
                m.put("startAt", it.startAt());
                m.put("endAt", it.endAt());
                m.put("status", it.status());
                String stockRaw = redis.opsForValue().get(Keys.stock(it.activityId()));
                m.put("warmed", stockRaw != null);
                m.put("redisStock", stockRaw == null ? null : Integer.parseInt(stockRaw));
                m.put("dbStock", dbStock);
                items.add(m);
            }
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("id", s.id());
            sm.put("label", s.label());
            sm.put("startAt", s.startAt());
            sm.put("endAt", s.endAt());
            sm.put("status", s.status());
            sm.put("items", items);
            sessions.add(sm);
        }
        Map<Object, Object> metricsRaw = redis.opsForHash().entries(Keys.METRICS);
        Map<String, Integer> metrics = new LinkedHashMap<>();
        for (String f : List.of("totalRequests", "rateLimited", "illegalPath", "repeatBlocked", "soldOut",
                "enqueued", "ordersCreated", "optimisticFail")) {
            Object v = metricsRaw.get(f);
            metrics.put(f, v == null ? 0 : Integer.parseInt(String.valueOf(v)));
        }
        return Map.of("sessions", sessions, "metrics", metrics);
    }

    public boolean warmup(String activityId) {
        SeckillActivity a = activityMapper.selectById(activityId);
        if (a == null) throw new BizException(7001, "活动不存在");
        activityMapper.resetStock(activityId);
        redis.opsForValue().set(Keys.stock(activityId), String.valueOf(a.getTotalStock()));
        redis.delete(Keys.bought(activityId));
        redis.delete(Keys.inflight(activityId));
        ops.log("SECKILL", "[预热] SET seckill:stock:" + activityId + " " + a.getTotalStock() + "；DEL seckill:bought:" + activityId);
        return true;
    }
}
