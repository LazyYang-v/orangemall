package com.orangemall.seckill.service;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 秒杀落单合并扣减（OM-025）的 DB 不变量，跑在真实 MySQL 8 + Flyway 迁移出的表上：
 * 不超卖、一人一单、重复投递幂等、库存不足时整批回滚后逐条先到先得。
 * JDBC 参数与线上一致（rewriteBatchedStatements=true），INSERT IGNORE 的影响行数语义与生产相同。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = SeckillDeductBatcherTest.Cfg.class)
class SeckillDeductBatcherTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mall_seckill").withUsername("root").withPassword("root")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        // spring.flyway.url 引用 ${spring.datasource.url}：覆盖它即同时指向容器
        r.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true");
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.datasource.hikari.maximum-pool-size", () -> "20");
    }

    @Configuration
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
            TransactionAutoConfiguration.class, JdbcTemplateAutoConfiguration.class, FlywayAutoConfiguration.class, MybatisPlusAutoConfiguration.class})
    @MapperScan("com.orangemall.seckill.mapper")
    @Import(SeckillDeductBatcher.class)
    static class Cfg {
    }

    @Autowired SeckillDeductBatcher batcher;
    @Autowired JdbcTemplate jdbc;

    private static final AtomicLong ORDER_IDS = new AtomicLong(9_000_000_000L);
    private String aid;

    @BeforeEach
    void activity() {
        aid = "S" + System.nanoTime() + "-1";
    }

    private void createActivity(int stock) {
        jdbc.update("INSERT INTO seckill_activity (id, session_id, sku_id, spu_id, seckill_price, total_stock, stock, limit_per_user, start_at, end_at)"
                + " VALUES (?, 'S1', 1, 1, 100, ?, ?, 1, NOW(3), NOW(3) + INTERVAL 1 HOUR)", aid, stock, stock);
    }

    private int stock() {
        return jdbc.queryForObject("SELECT stock FROM seckill_activity WHERE id = ?", Integer.class, aid);
    }

    private int orders() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM seckill_order WHERE activity_id = ?", Integer.class, aid);
    }

    /** 并发提交：每个元素是一个 userId；返回每次 deduct 的结果 */
    private List<Boolean> deductConcurrently(List<Long> users, int threads) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Boolean>> calls = new ArrayList<>();
            for (long u : users) calls.add(() -> batcher.deduct(aid, u, ORDER_IDS.incrementAndGet()));
            List<Boolean> out = new ArrayList<>();
            for (Future<Boolean> f : pool.invokeAll(calls)) out.add(f.get());
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void flywayMigratesFreshDatabaseToLatest() {
        Integer applied = jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForList("SHOW TABLES", String.class))
                .contains("seckill_activity", "seckill_order", "seckill_dead_letter", "undo_log");
    }

    @Test
    void concurrentBuyersNeverOversellAndEachUserBuysOnce() throws Exception {
        createActivity(100);
        List<Long> users = new ArrayList<>();
        for (long u = 1; u <= 400; u++) users.add(u);

        List<Boolean> results = deductConcurrently(users, 48);

        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(100);
        assertThat(stock()).isZero();
        assertThat(orders()).isEqualTo(100);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT user_id) FROM seckill_order WHERE activity_id = ?", Integer.class, aid))
                .isEqualTo(100);
    }

    @Test
    void redeliveredMessageIsIdempotent() throws Exception {
        createActivity(10);
        // 同一用户的消息被重复投递 5 次（Kafka 至少一次）：都返回「已成交」，但只扣 1 件、只有 1 条成交
        List<Boolean> results = deductConcurrently(List.of(7L, 7L, 7L, 7L, 7L, 8L), 6);

        assertThat(results).containsOnly(true);
        assertThat(stock()).isEqualTo(8);
        assertThat(orders()).isEqualTo(2);
    }

    @Test
    void insufficientStockFallsBackToFirstComeFirstServed() {
        createActivity(1);
        assertThat(batcher.deduct(aid, 1, ORDER_IDS.incrementAndGet())).isTrue();
        assertThat(batcher.deduct(aid, 2, ORDER_IDS.incrementAndGet())).isFalse();
        // 售罄后已成交用户的重投仍幂等成功，不受库存影响
        assertThat(batcher.deduct(aid, 1, ORDER_IDS.incrementAndGet())).isTrue();
        assertThat(stock()).isZero();
        assertThat(orders()).isEqualTo(1);
    }
}
