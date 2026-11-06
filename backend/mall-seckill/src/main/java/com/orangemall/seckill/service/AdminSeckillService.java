package com.orangemall.seckill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangemall.common.BizException;
import com.orangemall.common.Const;
import com.orangemall.seckill.config.SeckillProps;
import com.orangemall.seckill.entity.SeckillActivity;
import com.orangemall.seckill.mapper.SeckillActivityMapper;
import com.orangemall.seckill.support.Keys;
import com.orangemall.web.OpsLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 后台秒杀管理：视图聚合、手动预热、并发模拟（真实走 Lua + Kafka 全链路）、缓存一致性。
 */
@Slf4j
@Service
public class AdminSeckillService {
    private final SeckillService seckill;
    private final SeckillActivityMapper activityMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper om;
    private final SeckillProps props;
    private final KafkaTemplate<Object, Object> kafka;
    private final OpsLogger ops;
    private final ExecutorService simulatePool;
    private final DefaultRedisScript<Long> stockScript;
    private final DefaultRedisScript<List> repairScript;

    public AdminSeckillService(SeckillService seckill, SeckillActivityMapper activityMapper,
                               StringRedisTemplate redis, ObjectMapper om, SeckillProps props,
                               KafkaTemplate<Object, Object> kafka, OpsLogger ops,
                               @Qualifier("simulatePool") ExecutorService simulatePool,
                               @Qualifier("stockScript") DefaultRedisScript<Long> stockScript,
                               @Qualifier("repairScript") DefaultRedisScript<List> repairScript) {
        this.seckill = seckill;
        this.activityMapper = activityMapper;
        this.redis = redis;
        this.om = om;
        this.props = props;
        this.kafka = kafka;
        this.ops = ops;
        this.simulatePool = simulatePool;
        this.stockScript = stockScript;
        this.repairScript = repairScript;
    }

    public Map<String, Object> view() {
        return seckill.adminView();
    }

    public boolean warmup(String activityId) {
        return seckill.warmup(activityId);
    }

    /**
     * 并发模拟：绕过令牌桶与 path（对应 Mock 的 funnel 语义），N 个模拟用户并发执行
     * 第三层 Lua（判重 + 扣减）→ 第四层 Kafka。等待消费在途归零后返回漏斗与两侧库存。
     */
    public Map<String, Object> simulate(String activityId, int users) {
        SeckillActivity a = activityMapper.selectById(activityId);
        if (a == null) throw new BizException(7001, "活动不存在");
        String status = seckill.item(activityId).status();
        if (!"ONGOING".equals(status)) throw new BizException(7002, "仅进行中的活动可模拟");
        if (redis.opsForValue().get(Keys.stock(activityId)) == null) seckill.warmup(activityId);
        int n = Math.min(users, 5000);

        long m0 = metrics(); // enqueued 指标基线（未使用，保留以便后续扩展）
        AtomicInteger repeat = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        AtomicInteger success = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(n);
        long base = System.currentTimeMillis() % 1_000_000_000L;
        for (int i = 0; i < n; i++) {
            long simUser = -(base + i); // 负数模拟用户，与真实用户隔离
            simulatePool.execute(() -> {
                try {
                    Long r = redis.execute(stockScript,
                            List.of(Keys.stock(activityId), Keys.bought(activityId), Keys.inflight(activityId),
                                    Keys.METRICS, Keys.result(activityId, simUser)),
                            String.valueOf(simUser), "{\"state\":\"QUEUING\"}",
                            String.valueOf(props.getResultTtlSeconds()));
                    if (r == null || r == -2 || r == -3) soldOut.incrementAndGet();
                    else if (r == -1) repeat.incrementAndGet();
                    else {
                        success.incrementAndGet();
                        Map<String, Object> msg = new LinkedHashMap<>();
                        msg.put("orderId", com.orangemall.common.IdGenerator.nextId(simUser & 3));
                        msg.put("userId", simUser);
                        msg.put("activityId", activityId);
                        msg.put("skuId", a.getSkuId());
                        msg.put("seckillPrice", a.getSeckillPrice());
                        msg.put("sim", true);
                        kafka.send(Const.KAFKA_SECKILL_TOPIC, String.valueOf(simUser), om.writeValueAsString(msg));
                    }
                } catch (Exception e) {
                    log.warn("sim user failed: {}", e.getMessage());
                } finally {
                    done.countDown();
                }
            });
        }
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // 等待消费在途归零（最多 20s）
        for (int i = 0; i < 200; i++) {
            String inf = redis.opsForValue().get(Keys.inflight(activityId));
            if (inf == null || "0".equals(inf)) break;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        long m1 = metrics();
        String redisLeft = redis.opsForValue().get(Keys.stock(activityId));
        Integer dbLeft = activityMapper.stockOf(activityId);
        ops.log("SECKILL", "[模拟] " + n + " 个请求：成功 " + success.get() + " / 售罄 " + soldOut.get()
                + " / 重复 " + repeat.get() + "，Redis 剩余 " + redisLeft + "，DB 剩余 " + dbLeft);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("requests", n);
        out.put("success", success.get());
        out.put("soldOut", soldOut.get());
        out.put("limited", 0);
        out.put("redisLeft", redisLeft == null ? 0 : Integer.parseInt(redisLeft));
        out.put("dbLeft", dbLeft == null ? 0 : dbLeft);
        out.put("repeatBlocked", repeat.get());
        return out;
    }

    private long metrics() {
        Object v = redis.opsForHash().get(Keys.METRICS, "enqueued");
        return v == null ? 0 : Long.parseLong(String.valueOf(v));
    }

    public Map<String, Object> cacheCheck() {
        // 最近 12 个有 Redis 库存的活动（TreeSet 反序 = 新场次在前）
        Set<String> aids = redis.keys("seckill:stock:*");
        List<Map<String, Object>> rows = new ArrayList<>();
        if (aids != null) {
            List<String> keys = new ArrayList<>(aids);
            keys.sort(Comparator.reverseOrder());
            for (String k : keys.subList(0, Math.min(12, keys.size()))) {
                String aid = k.substring("seckill:stock:".length());
                String raw = redis.opsForValue().get(k);
                Integer db = activityMapper.stockOf(aid);
                String infRaw = redis.opsForValue().get(Keys.inflight(aid));
                int inflight = infRaw == null ? 0 : Integer.parseInt(infRaw);
                int dbVal = db == null ? 0 : db;
                int expect = dbVal - inflight;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("key", k);
                row.put("redisValue", raw == null ? null : Integer.parseInt(raw));
                row.put("dbValue", dbVal);
                row.put("consistent", raw != null && Integer.parseInt(raw) == expect);
                rows.add(row);
            }
        }
        Map<Object, Object> stats = redis.opsForHash().entries(props.getCacheStatsKey());
        int hit = num(stats.get("hit"));
        int miss = num(stats.get("miss"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", rows);
        out.put("hitRate", hit + miss == 0 ? 0 : (double) hit / (hit + miss));
        out.put("hit", hit);
        out.put("miss", miss);
        return out;
    }

    /** 以 DB 为准修复（等待在途归零的语义由 repair.lua 决定；手动修复强制写 DB 值） */
    @SuppressWarnings("unchecked")
    public boolean repair(String key) {
        String aid = key.startsWith("seckill:stock:") ? key.substring("seckill:stock:".length()) : key;
        Integer db = activityMapper.stockOf(aid);
        if (db == null) throw new BizException(7001, "活动不存在");
        redis.execute(repairScript, List.of(Keys.stock(aid), Keys.inflight(aid)), String.valueOf(db), "0");
        ops.log("CACHE", "[一致性校验] " + key + " 以 DB 为准修复 Redis = " + db);
        return true;
    }

    private int num(Object o) {
        return o == null ? 0 : Integer.parseInt(String.valueOf(o));
    }
}
