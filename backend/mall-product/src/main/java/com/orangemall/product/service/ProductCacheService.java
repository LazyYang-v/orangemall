package com.orangemall.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangemall.common.BizException;
import com.orangemall.product.vo.SpuDetailVO;
import com.orangemall.web.OpsLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 商品详情缓存 product:detail:{spuId}
 * <ul>
 *   <li>Cache Aside：读缓存 → 未命中查库回填（TTL 30min + 0~5min 随机抖动，防雪崩）</li>
 *   <li>空值缓存：库里不存在的 ID 写入占位符 TTL 60s + 抖动（防穿透）</li>
 *   <li>单飞锁：同一 spuId 在本实例只放一个线程回源（防击穿）</li>
 *   <li>写库 → DEL → 500ms 后再 DEL（延迟双删，防并发读把旧值回填）；事务内调用则在提交后执行</li>
 *   <li>命中统计：Redis Hash product:cache:stats {hit, miss}（后台缓存页可读）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCacheService {
    public static final String K_DETAIL = "product:detail:";
    public static final String K_STATS = "product:cache:stats";
    public static final String NULL_MARK = "__NULL__";
    private static final long TTL_SECONDS = 30 * 60;
    private static final int TTL_JITTER = 5 * 60;
    private static final long NULL_TTL_SECONDS = 60;
    private static final long SECOND_DELETE_DELAY_MS = 500;

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final OpsLogger ops;
    private final ScheduledExecutorService cacheDeleteScheduler;
    private final ConcurrentHashMap<Long, Object> loadLocks = new ConcurrentHashMap<>();

    /** @param loader 回源查库，不存在返回 null */
    public SpuDetailVO get(long spuId, Function<Long, SpuDetailVO> loader) {
        String key = K_DETAIL + spuId;
        SpuDetailVO v = readCache(key);
        if (v != null) return v;
        Object lock = loadLocks.computeIfAbsent(spuId, k -> new Object());
        try {
            synchronized (lock) {
                v = readCache(key); // double check：排队线程直接吃到第一个线程的回填
                if (v != null) return v;
                stat("miss");
                SpuDetailVO db = loader.apply(spuId);
                if (db == null) {
                    long ttl = NULL_TTL_SECONDS + ThreadLocalRandom.current().nextInt(30);
                    redis.opsForValue().set(key, NULL_MARK, Duration.ofSeconds(ttl));
                    ops.log("CACHE", "[空值缓存] " + key + " 数据库不存在 → 写入空值占位（TTL " + ttl + "s），防缓存穿透");
                    throw notFound();
                }
                long ttl = TTL_SECONDS + ThreadLocalRandom.current().nextInt(TTL_JITTER);
                db.setFavorited(null);
                redis.opsForValue().set(key, mapper.writeValueAsString(db), Duration.ofSeconds(ttl));
                ops.log("CACHE", "[Cache Aside] " + key + " 未命中 → 查库回填 Redis（TTL 30min + 随机抖动）");
                return db;
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            loadLocks.remove(spuId, lock);
        }
    }

    private SpuDetailVO readCache(String key) {
        String json;
        try {
            json = redis.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("redis read failed, fallback to db: {}", e.getMessage());
            return null;
        }
        if (json == null) return null;
        stat("hit");
        if (NULL_MARK.equals(json)) throw notFound();
        try {
            return mapper.readValue(json, SpuDetailVO.class);
        } catch (Exception e) {
            log.warn("bad cache value {}, drop it", key);
            redis.delete(key);
            return null;
        }
    }

    private static BizException notFound() {
        return BizException.of(2001, "商品不存在或已下架");
    }

    private void stat(String field) {
        try {
            redis.opsForHash().increment(K_STATS, field, 1);
        } catch (Exception ignored) {
        }
    }

    /** 后台改价/改库存/上下架：延迟双删，并写链路日志（文案对齐 Mock） */
    public void delayDoubleDelete(long spuId) {
        afterCommit(() -> {
            String key = K_DETAIL + spuId;
            redis.delete(key);
            ops.log("CACHE", "[延迟双删] UPDATE DB → DEL " + key + "（第 1 次）");
            cacheDeleteScheduler.schedule(() -> {
                try {
                    redis.delete(key);
                    ops.log("CACHE", "[延迟双删] 500ms 后 DEL " + key + "（第 2 次），防并发读回填旧值");
                } catch (Exception e) {
                    log.warn("second delete failed {}: {}", key, e.getMessage());
                }
            }, SECOND_DELETE_DELAY_MS, TimeUnit.MILLISECONDS);
        });
    }

    /** 库存扣减/回补：同样延迟双删，但不写后台链路日志（高频） */
    public void evictQuietly(Collection<Long> spuIds) {
        Set<String> keys = new LinkedHashSet<>();
        spuIds.forEach(id -> keys.add(K_DETAIL + id));
        if (keys.isEmpty()) return;
        afterCommit(() -> {
            try {
                redis.delete(keys);
            } catch (Exception e) {
                log.warn("evict failed: {}", e.getMessage());
            }
            cacheDeleteScheduler.schedule(() -> {
                try {
                    redis.delete(keys);
                } catch (Exception ignored) {
                }
            }, SECOND_DELETE_DELAY_MS, TimeUnit.MILLISECONDS);
            log.debug("stock changed, evicted {}", keys);
        });
    }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }
}
