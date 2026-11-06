package com.orangemall.product.service;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.orangemall.api.dto.StockItem;
import com.orangemall.api.dto.StockOpReq;
import com.orangemall.common.BizException;
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
import org.springframework.boot.test.mock.mockito.MockBean;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 库存流水（V3 stock_ledger）的 DB 不变量，跑在真实 MySQL 8 + Flyway 迁移出的表上：
 * 扣减按订单幂等、扣减失败流水随本地事务回滚、回补按订单只生效一次（含并发）、回补件数以扣减流水为准、
 * V3 之前的历史订单（无流水）按请求件数回补且同样只回补一次。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = StockLedgerTest.Cfg.class)
class StockLedgerTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mall_product").withUsername("root").withPassword("root")
            .withEnv("TZ", "Asia/Shanghai") // 与 compose 一致：created_at 取 DB 时钟，JDBC 按 serverTimezone=Asia/Shanghai 换算 Instant
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai");
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.datasource.hikari.maximum-pool-size", () -> "16");
    }

    @Configuration
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
            TransactionAutoConfiguration.class, JdbcTemplateAutoConfiguration.class, FlywayAutoConfiguration.class, MybatisPlusAutoConfiguration.class})
    @MapperScan("com.orangemall.product.mapper")
    @Import(StockService.class)
    static class Cfg {
    }

    @MockBean ProductService productService;
    @MockBean CategoryService categoryService;
    @MockBean ProductCacheService cache;
    @Autowired StockService stock;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.orangemall.product.mapper.StockLedgerMapper ledger;

    private static final long A = 100101L, B = 100102L;
    private static final AtomicLong ORDER_IDS = new AtomicLong(8_000_000_000L);
    private long orderId;

    @BeforeEach
    void reset() {
        jdbc.update("UPDATE sku SET stock = 10, sales = 100 WHERE id IN (?, ?)", A, B);
        orderId = ORDER_IDS.incrementAndGet();
    }

    private int stockOf(long sku) {
        return jdbc.queryForObject("SELECT stock FROM sku WHERE id = ?", Integer.class, sku);
    }

    private int ledgerRows(String op) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM stock_ledger WHERE order_id = ? AND op = ?", Integer.class, orderId, op);
    }

    private static StockOpReq req(long orderId, StockItem... items) {
        return new StockOpReq(orderId, List.of(items));
    }

    @Test
    void deductIsIdempotentPerOrder() {
        stock.deduct(req(orderId, new StockItem(A, 2), new StockItem(B, 1)));
        stock.deduct(req(orderId, new StockItem(A, 2), new StockItem(B, 1))); // 重放
        assertThat(stockOf(A)).isEqualTo(8);
        assertThat(stockOf(B)).isEqualTo(9);
        assertThat(ledgerRows(StockService.DEDUCT)).isEqualTo(2);
    }

    @Test
    void failedDeductLeavesNoLedgerAndNoStockChange() {
        // A 够、B 不够：整笔本地事务回滚，A 的扣减与流水都不留
        assertThatThrownBy(() -> stock.deduct(req(orderId, new StockItem(A, 3), new StockItem(B, 11))))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(3001);
        assertThat(stockOf(A)).isEqualTo(10);
        assertThat(ledgerRows(StockService.DEDUCT)).isZero();
    }

    @Test
    void restoreHappensOnceEvenWhenRepeatedConcurrently() throws Exception {
        stock.deduct(req(orderId, new StockItem(A, 3)));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> fs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            fs.add(pool.submit(() -> {
                go.await();
                return stock.restore(req(orderId, new StockItem(A, 3)));
            }));
        }
        go.countDown();
        int applied = 0;
        for (Future<Integer> f : fs) applied += f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(applied).isEqualTo(1);
        assertThat(stockOf(A)).isEqualTo(10);
        assertThat(ledgerRows(StockService.RESTORE)).isEqualTo(1);
    }

    @Test
    void restoreQuantityComesFromLedgerNotCaller() {
        stock.deduct(req(orderId, new StockItem(A, 2)));
        // 调用方多报件数、或带上从未扣过的 SKU：只按流水回补 A 的 2 件，B 不动
        assertThat(stock.restore(req(orderId, new StockItem(A, 5), new StockItem(B, 4)))).isEqualTo(1);
        assertThat(stockOf(A)).isEqualTo(10);
        assertThat(stockOf(B)).isEqualTo(10);
    }

    @Test
    void legacyOrderWithoutLedgerRestoresRequestedCountOnce() {
        // V3 之前创建的订单没有 DEDUCT 流水：按请求件数回补，RESTORE 唯一键保证只一次
        assertThat(stock.restore(req(orderId, new StockItem(A, 2)))).isEqualTo(1);
        assertThat(stock.restore(req(orderId, new StockItem(A, 2)))).isZero();
        assertThat(stockOf(A)).isEqualTo(12);
    }

    @Test
    void reconcileQueriesSeeDeductsAndRestoresByOrderAndWindow() {
        long other = ORDER_IDS.incrementAndGet();
        stock.deduct(req(orderId, new StockItem(A, 2), new StockItem(B, 1)));
        stock.deduct(req(other, new StockItem(A, 1)));
        stock.restore(req(orderId, new StockItem(A, 2), new StockItem(B, 1)));

        List<com.orangemall.api.dto.LedgerEntry> rows = ledger.byOrders(List.of(orderId, other));
        assertThat(rows).extracting(r -> r.orderId() + ":" + r.skuId() + ":" + r.op() + ":" + r.qty()).containsExactlyInAnyOrder(
                orderId + ":" + A + ":DEDUCT:2", orderId + ":" + B + ":DEDUCT:1", orderId + ":" + A + ":RESTORE:2",
                orderId + ":" + B + ":RESTORE:1", other + ":" + A + ":DEDUCT:1");

        // 窗口取本例流水的 DB 时间（同一秒内其它用例也扣过库存）；[from, to) 左闭右开
        java.time.Instant before = jdbc.queryForObject("SELECT MIN(created_at) FROM stock_ledger WHERE order_id IN (?, ?)",
                java.sql.Timestamp.class, orderId, other).toInstant();
        java.time.Instant after = jdbc.queryForObject("SELECT MAX(created_at) FROM stock_ledger WHERE order_id IN (?, ?) AND op = 'DEDUCT'",
                java.sql.Timestamp.class, orderId, other).toInstant().plusMillis(1);
        // 同一毫秒里上一个用例的流水也可能落进窗口，所以只断言「包含、去重、升序、受 limit 限制」
        List<Long> win = ledger.deductedBetween(before, after, 100);
        assertThat(win).contains(orderId, other).doesNotHaveDuplicates().isSorted();
        assertThat(ledger.deductedBetween(before, after, 1)).hasSize(1);
        assertThat(ledger.deductedBetween(after.plusSeconds(60), after.plusSeconds(120), 100)).isEmpty();
    }
}
