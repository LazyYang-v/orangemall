package com.orangemall.marketing.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.orangemall.marketing.entity.CouponTemplate;
import com.orangemall.marketing.mapper.CouponTemplateMapper;
import com.orangemall.marketing.mapper.UserCouponMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 领券 Redis 计数（DB 为准，Redis 用于高并发原子判断）：
 * <ul>
 *   <li>{@code coupon:stock:{tplId}}  剩余量 = total - received</li>
 *   <li>{@code coupon:received:{tplId}}  Hash：userId → 已领张数</li>
 * </ul>
 * 启动时按 DB 全量预热；key 丢失（过期 / 被清）时按需加锁懒加载。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponStockCache implements ApplicationRunner {
    public static final String K_STOCK = "coupon:stock:";
    public static final String K_RECEIVED = "coupon:received:";
    private static final String K_WARM_LOCK = "coupon:warm:lock:";

    /** 0 成功；-1 未预热；1 已抢光；2 达到限领 */
    private static final DefaultRedisScript<Long> RECEIVE = new DefaultRedisScript<>("""
            local s = redis.call('GET', KEYS[1])
            if not s then return -1 end
            if tonumber(s) <= 0 then return 1 end
            local c = tonumber(redis.call('HGET', KEYS[2], ARGV[1]) or '0')
            if c >= tonumber(ARGV[2]) then return 2 end
            redis.call('DECR', KEYS[1])
            redis.call('HINCRBY', KEYS[2], ARGV[1], 1)
            return 0
            """, Long.class);

    /** 落库失败回补 */
    private static final DefaultRedisScript<Long> ROLLBACK = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then redis.call('INCR', KEYS[1]) end
            local c = tonumber(redis.call('HGET', KEYS[2], ARGV[1]) or '0')
            if c > 0 then redis.call('HINCRBY', KEYS[2], ARGV[1], -1) end
            return 1
            """, Long.class);

    /** 系统发券后同步（已预热才同步，未预热的下次懒加载会从 DB 读到） */
    private static final DefaultRedisScript<Long> GRANTED = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            redis.call('DECR', KEYS[1])
            redis.call('HINCRBY', KEYS[2], ARGV[1], 1)
            return 1
            """, Long.class);

    /** 预热：先重建 Hash 再 SET stock（stock 存在即视为已预热，最后写保证原子可见） */
    private static final DefaultRedisScript<Long> WARM = new DefaultRedisScript<>("""
            redis.call('DEL', KEYS[2])
            for i = 3, #ARGV, 2 do redis.call('HSET', KEYS[2], ARGV[i], ARGV[i + 1]) end
            redis.call('SET', KEYS[1], ARGV[1])
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            if redis.call('EXISTS', KEYS[2]) == 1 then redis.call('EXPIRE', KEYS[2], ARGV[2]) end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final CouponTemplateMapper templateMapper;
    private final UserCouponMapper userCouponMapper;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<CouponTemplate> all = templateMapper.selectList(Wrappers.lambdaQuery());
            all.forEach(this::warm);
            log.info("coupon stock warmed: {} templates", all.size());
        } catch (Exception e) {
            log.error("coupon stock warm-up failed (will lazy load)", e);
        }
    }

    public void warm(CouponTemplate t) {
        List<String> argv = new ArrayList<>();
        argv.add(String.valueOf(Math.max(0, t.getTotal() - t.getReceived())));
        argv.add(String.valueOf(ttlSeconds(t)));
        for (Map<String, Object> row : userCouponMapper.countByUser(t.getId())) {
            argv.add(String.valueOf(row.get("k")));
            argv.add(String.valueOf(row.get("c")));
        }
        redis.execute(WARM, keys(t.getId()), argv.toArray());
    }

    /** @return 0 成功 / 1 抢光 / 2 限领 */
    public int tryReceive(CouponTemplate t, long userId) {
        for (int i = 0; i < 40; i++) {
            Long r = redis.execute(RECEIVE, keys(t.getId()), String.valueOf(userId), String.valueOf(t.getPerUserLimit()));
            if (r != null && r >= 0) return r.intValue();
            lazyWarm(t.getId());
        }
        throw new IllegalStateException("coupon stock warm-up timeout: " + t.getId());
    }

    public void rollback(String templateId, long userId) {
        try {
            redis.execute(ROLLBACK, keys(templateId), String.valueOf(userId));
        } catch (Exception e) {
            log.error("coupon redis rollback failed tpl={} user={}", templateId, userId, e);
        }
    }

    public void onGranted(String templateId, long userId) {
        try {
            redis.execute(GRANTED, keys(templateId), String.valueOf(userId));
        } catch (Exception e) {
            log.warn("coupon redis grant sync failed tpl={} user={}: {}", templateId, userId, e.getMessage());
        }
    }

    /** 未预热：抢到锁的线程读 DB 预热，其它线程稍等后重试 Lua */
    private void lazyWarm(String templateId) {
        Boolean locked = redis.opsForValue().setIfAbsent(K_WARM_LOCK + templateId, "1", Duration.ofSeconds(5));
        if (Boolean.TRUE.equals(locked)) {
            try {
                if (Boolean.FALSE.equals(redis.hasKey(K_STOCK + templateId))) {
                    CouponTemplate fresh = templateMapper.selectById(templateId);
                    if (fresh != null) warm(fresh);
                }
            } finally {
                redis.delete(K_WARM_LOCK + templateId);
            }
        } else {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static List<String> keys(String templateId) {
        return List.of(K_STOCK + templateId, K_RECEIVED + templateId);
    }

    /** 活动结束后再保留 1 天 */
    private static long ttlSeconds(CouponTemplate t) {
        long s = Duration.between(Instant.now(), t.getEndAt()).getSeconds() + 86400;
        return Math.max(86400, s);
    }
}
