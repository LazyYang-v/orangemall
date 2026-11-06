package com.orangemall.seckill.job;

import com.orangemall.common.Const;
import com.orangemall.seckill.entity.SeckillActivity;
import com.orangemall.seckill.service.SeckillReliabilityService;
import com.orangemall.seckill.support.KafkaLagProbe;
import com.orangemall.seckill.mapper.SeckillActivityMapper;
import com.orangemall.seckill.support.Keys;
import com.orangemall.seckill.support.SeckillCalendar;
import com.orangemall.web.OpsLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.function.LongSupplier;

/**
 * 滚动建场与一致性校验：
 * 1. 每 30s（Redisson 锁防多实例并发）：k=-1..2 场次的 8 个商品 INSERT IGNORE 建场；
 *    ONGOING/ENDED 场次自动预热（DB stock = 总量 - 预热已售比例，再 SETNX 到 Redis）。
 * 2. 每 60s 一致性校验：Redis 库存 ?= DB 库存 - 在途数；在途为 0 且不一致时以 DB 为准自动修复。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillJob {
    private final SeckillActivityMapper activityMapper;
    private final StringRedisTemplate redis;
    private final RedissonClient redisson;
    private final OpsLogger ops;
    private final SeckillReliabilityService reliability;
    private final KafkaLagProbe lagProbe;

    @Scheduled(fixedDelay = 30_000, initialDelay = 5_000)
    public void rollSessions() {
        RLock lock = redisson.getLock(Keys.LOCK_ROLL);
        if (!lock.tryLock()) return;
        try {
            long now = System.currentTimeMillis();
            for (SeckillCalendar.Session s : SeckillCalendar.window(now)) {
                String status = s.status(now);
                for (SeckillCalendar.Tpl tpl : SeckillCalendar.rotated(s.start())) {
                    String aid = SeckillCalendar.activityId(s.id(), tpl.skuId());
                    SeckillActivity a = activityMapper.selectById(aid);
                    if (a == null) {
                        a = new SeckillActivity();
                        a.setId(aid);
                        a.setSessionId(s.id());
                        a.setSkuId(tpl.skuId());
                        a.setSpuId(tpl.spuId());
                        a.setName("热销爆品 #" + tpl.skuId());
                        a.setSeckillPrice(tpl.price());
                        a.setTotalStock(tpl.stock());
                        a.setStock(tpl.stock());
                        a.setLimitPerUser(1);
                        a.setStartAt(java.time.Instant.ofEpochMilli(s.start()));
                        a.setEndAt(java.time.Instant.ofEpochMilli(s.end()));
                        a.setWarmed(0);
                        activityMapper.insertIgnore(a);
                        ops.log("SECKILL", "[建场] 活动 " + aid + " 创建（场次 " + s.label() + "）");
                        a = activityMapper.selectById(aid);
                    }
                    // 已开始 / 已结束场次自动预热（只一次）；未开始场次留给后台手动预热或开抢时预热
                    if (a != null && a.getWarmed() == 0 && !"UPCOMING".equals(status)) {
                        int sold = SeckillCalendar.warmSold(aid, tpl.stock(), "ENDED".equals(status));
                        int remain = Math.max(0, tpl.stock() - sold);
                        activityMapper.firstWarm(aid, remain);
                        redis.opsForValue().setIfAbsent(Keys.stock(aid), String.valueOf(remain));
                        if ("ONGOING".equals(status)) {
                            ops.log("SECKILL", "[预热] " + aid + " 自动预热，库存 " + remain);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("rollSessions failed: {}", e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    public void consistencyCheck() {
        RLock lock = redisson.getLock(Keys.LOCK_CONSISTENCY);
        if (!lock.tryLock()) return;
        try {
            long now = System.currentTimeMillis();
            // 一轮最多探测一次 Kafka 积压，且只在确有滞留候选时才探测
            long[] lagCache = {Long.MIN_VALUE};
            LongSupplier lag = () -> lagCache[0] != Long.MIN_VALUE ? lagCache[0]
                    : (lagCache[0] = lagProbe.lag(Const.KAFKA_SECKILL_TOPIC));
            for (SeckillCalendar.Session s : SeckillCalendar.window(now)) {
                for (SeckillCalendar.Tpl tpl : SeckillCalendar.rotated(s.start())) {
                    String aid = SeckillCalendar.activityId(s.id(), tpl.skuId());
                    // OM-013：在途滞留巡检（可能触发自动重置，故之后重新读取在途/库存）
                    try {
                        reliability.watchInflight(aid, readInt(Keys.inflight(aid)), now, lag);
                    } catch (Exception e) {
                        log.warn("inflight watch {} failed: {}", aid, e.toString());
                    }
                    String raw = redis.opsForValue().get(Keys.stock(aid));
                    if (raw == null) continue;
                    Integer dbStock = activityMapper.stockOf(aid);
                    if (dbStock == null) continue;
                    int inflight = readInt(Keys.inflight(aid));
                    int expect = dbStock - inflight;
                    if (Integer.parseInt(raw) != expect) {
                        ops.log("CACHE", "[一致性校验] " + Keys.stock(aid) + " Redis=" + raw
                                + " DB=" + dbStock + " 在途=" + inflight + (inflight == 0 ? " → 自动修复" : " → 等待在途归零"));
                        // 已结束场次同样以 DB 为准修复（不再售卖，修复无副作用），避免不一致每分钟刷屏
                        if (inflight == 0 && !"UPCOMING".equals(s.status(now))) {
                            redis.opsForValue().set(Keys.stock(aid), String.valueOf(expect));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("consistencyCheck failed: {}", e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private int readInt(String key) {
        String v = redis.opsForValue().get(key);
        return v == null ? 0 : Integer.parseInt(v);
    }
}
