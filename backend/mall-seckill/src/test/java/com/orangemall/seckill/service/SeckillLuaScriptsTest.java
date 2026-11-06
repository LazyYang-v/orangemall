package com.orangemall.seckill.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 秒杀 Redis 层 Lua 脚本（第一~三层防线 + 消费结束）在真实 Redis 7 上的原子性与边界：
 * stock.lua 并发不超卖 / 一人一单 / 在途计数；finish.lua 补偿回补且在途不为负；guard.lua 令牌桶 / path / 风控。
 * 脚本文本直接取自 src/main/resources/lua，与生产同一份。
 */
@Testcontainers(disabledWithoutDocker = true)
class SeckillLuaScriptsTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.0-alpine").withExposedPorts(6379);

    static LettuceConnectionFactory cf;
    static StringRedisTemplate redis;
    static DefaultRedisScript<Long> stock, finish, guard;

    static final String A = "test-activity";
    static final String STOCK = "seckill:stock:" + A, BOUGHT = "seckill:bought:" + A, INFLIGHT = "seckill:inflight:" + A,
            METRICS = "seckill:metrics:" + A;

    @BeforeAll
    static void connect() throws Exception {
        cf = new LettuceConnectionFactory(new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        cf.afterPropertiesSet();
        redis = new StringRedisTemplate(cf);
        stock = script("stock.lua");
        finish = script("finish.lua");
        guard = script("guard.lua");
    }

    @AfterAll
    static void close() {
        cf.destroy();
    }

    static DefaultRedisScript<Long> script(String name) throws Exception {
        String text = new ClassPathResource("lua/" + name).getContentAsString(StandardCharsets.UTF_8);
        return new DefaultRedisScript<>(text, Long.class);
    }

    @BeforeEach
    void flush() {
        redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) c -> {
            c.serverCommands().flushAll();
            return null;
        });
    }

    long buy(long userId) {
        return redis.execute(stock, List.of(STOCK, BOUGHT, INFLIGHT, METRICS, "seckill:result:" + A + ":" + userId,
                "seckill:path:" + A + ":" + userId), String.valueOf(userId), "{\"status\":\"QUEUING\"}", "600");
    }

    long finish(long userId, boolean restore, boolean removeBought) {
        return redis.execute(finish, List.of(STOCK, BOUGHT, INFLIGHT, METRICS, "seckill:result:" + A + ":" + userId),
                String.valueOf(userId), "{\"status\":\"FAILED\"}", "600", restore ? "1" : "0", removeBought ? "1" : "0", "compensated");
    }

    @Test
    void notWarmedActivityIsRejected() {
        assertThat(buy(1)).isEqualTo(-2);
    }

    @Test
    void concurrentAttemptsNeverOversellAndOneOrderPerUser() throws Exception {
        redis.opsForValue().set(STOCK, "50");
        // 300 个用户各抢 2 次（重复点击），48 线程并发
        List<Callable<Long>> calls = new ArrayList<>();
        for (long u = 1; u <= 300; u++) {
            long uid = u;
            calls.add(() -> buy(uid));
            calls.add(() -> buy(uid));
        }
        ExecutorService pool = Executors.newFixedThreadPool(48);
        List<Long> codes = new ArrayList<>();
        try {
            for (Future<Long> f : pool.invokeAll(calls)) codes.add(f.get());
        } finally {
            pool.shutdownNow();
        }

        long ok = codes.stream().filter(c -> c >= 0).count();
        assertThat(ok).isEqualTo(50);
        assertThat(codes).allMatch(c -> c >= 0 || c == -1 || c == -3);
        assertThat(redis.opsForValue().get(STOCK)).isEqualTo("0");
        Set<String> bought = redis.opsForSet().members(BOUGHT);
        assertThat(bought).hasSize(50);
        assertThat(redis.opsForValue().get(INFLIGHT)).isEqualTo("50");
        assertThat(redis.opsForHash().get(METRICS, "enqueued")).isEqualTo("50");
        assertThat(redis.opsForValue().get("seckill:result:" + A + ":" + bought.iterator().next())).contains("QUEUING");
    }

    @Test
    void compensationRestoresStockAndLetsUserRetry_inflightNeverNegative() {
        redis.opsForValue().set(STOCK, "1");
        assertThat(buy(9)).isZero();
        assertThat(buy(10)).isEqualTo(-3);

        finish(9, true, true); // 落单失败补偿：回补库存 + 移出已购
        assertThat(redis.opsForValue().get(STOCK)).isEqualTo("1");
        assertThat(redis.opsForSet().isMember(BOUGHT, "9")).isFalse();
        assertThat(redis.opsForValue().get(INFLIGHT)).isEqualTo("0");
        assertThat(redis.opsForValue().get("seckill:result:" + A + ":9")).contains("FAILED");

        finish(9, false, false); // 重复结束（重投）：在途钳在 0
        assertThat(redis.opsForValue().get(INFLIGHT)).isEqualTo("0");

        assertThat(buy(10)).isZero(); // 回补的库存可再被抢
    }

    long guard(long now, int cap, int rate, String path, long userId, int minCredit) {
        return redis.execute(guard, List.of("seckill:bucket:" + userId, METRICS, "seckill:path:" + A + ":" + userId,
                        "risk:credit", "risk:blocked"),
                String.valueOf(now), String.valueOf(cap), String.valueOf(rate), path, String.valueOf(userId), String.valueOf(minCredit));
    }

    @Test
    void guardTokenBucketPathAndRisk() {
        long now = 1_000_000L;
        redis.opsForValue().set("seckill:path:" + A + ":1", "p-ok");
        for (int i = 0; i < 3; i++) assertThat(guard(now, 3, 1, "p-ok", 1, 60)).isZero();
        assertThat(guard(now, 3, 1, "p-ok", 1, 60)).isEqualTo(1);          // 桶空：限流
        assertThat(guard(now + 1000, 3, 1, "p-ok", 1, 60)).isZero();       // 1s 后补 1 个令牌
        assertThat(guard(now + 1000, 3, 1, "p-ok", 1, 60)).isEqualTo(1);

        assertThat(guard(now, 3, 1, "p-forged", 2, 60)).isEqualTo(2);      // path 不存在 / 不匹配

        redis.opsForValue().set("seckill:path:" + A + ":3", "p3");
        redis.opsForZSet().add("risk:credit", "3", 40);
        assertThat(guard(now, 3, 1, "p3", 3, 60)).isEqualTo(3);            // 信用分低于阈值
        redis.opsForValue().set("seckill:path:" + A + ":4", "p4");
        redis.opsForSet().add("risk:blocked", "4");
        assertThat(guard(now, 3, 1, "p4", 4, 60)).isEqualTo(3);            // 黑名单
        assertThat(redis.opsForHash().get(METRICS, "rateLimited")).isEqualTo("2");
    }
}
