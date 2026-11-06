package com.orangemall.order.service;

import com.orangemall.api.client.ProductInnerClient;
import com.orangemall.api.dto.LedgerEntry;
import com.orangemall.api.dto.StockItem;
import com.orangemall.common.Result;
import com.orangemall.order.domain.OrderRow;
import com.orangemall.order.mapper.OrderMapper;
import com.orangemall.web.OpsLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 库存对账三项核对：已关闭缺回补 → 自动补（失败转后台重试）；普通单无扣减流水 → 告警；扣减流水无订单 → 告警。
 * 订单号低 2 位 = 分表号，桩按分表返回。
 */
class StockReconcileServiceTest {
    private final OrderMapper mapper = mock(OrderMapper.class);
    private final ProductInnerClient product = mock(ProductInnerClient.class);
    private final OrderService orders = mock(OrderService.class);
    private final OpsLogger ops = mock(OpsLogger.class);
    private final StockReconcileService svc = new StockReconcileService(mapper, product, orders, ops);
    private final Instant from = Instant.parse("2026-11-06T10:00:00Z"), to = Instant.parse("2026-11-06T10:30:00Z");

    // 分表 0：100（已取消，已回补）、104（已关闭，两个 SKU 只回补了一个）；分表 1：101（已关闭，从没回补）
    static final long OK = 100, PARTIAL = 104, LOST = 101;

    @BeforeEach
    void setUp() {
        when(mapper.closedNormalBetween(anyString(), any(), any(), anyInt())).thenReturn(List.of());
        when(mapper.createdNormalBetween(anyString(), any(), any(), anyInt())).thenReturn(List.of());
        when(mapper.closedNormalBetween(eq("0"), eq(from), eq(to), anyInt())).thenReturn(List.of(OK, PARTIAL));
        when(mapper.closedNormalBetween(eq("1"), eq(from), eq(to), anyInt())).thenReturn(List.of(LOST));
        when(product.ledgerByOrders(anyList())).thenReturn(Result.ok(List.of(
                new LedgerEntry(OK, 1L, "DEDUCT", 1), new LedgerEntry(OK, 1L, "RESTORE", 1),
                new LedgerEntry(PARTIAL, 1L, "DEDUCT", 2), new LedgerEntry(PARTIAL, 2L, "DEDUCT", 3), new LedgerEntry(PARTIAL, 1L, "RESTORE", 2),
                new LedgerEntry(LOST, 5L, "DEDUCT", 4))));
        when(product.restore(any())).thenReturn(Result.ok(1));
        when(product.deducted(anyLong(), anyLong(), anyInt())).thenReturn(Result.ok(List.of()));
    }

    @Test
    void closedOrdersMissingRestoreAreCompensatedWithExactlyTheMissingSkus() {
        StockReconcileService.Report r = svc.reconcile(from, to);
        assertThat(r.closedChecked()).isEqualTo(3);
        assertThat(r.restored()).containsExactlyInAnyOrder(PARTIAL, LOST);
        verify(product).restore(argThat(q -> q.orderId() == PARTIAL && q.items().equals(List.of(new StockItem(2L, 3)))));
        verify(product).restore(argThat(q -> q.orderId() == LOST && q.items().equals(List.of(new StockItem(5L, 4)))));
        verify(product, never()).restore(argThat(q -> q.orderId() == OK));
        verify(ops, never()).log(eq("ALERT"), anyString());
    }

    @Test
    void failedCompensationIsDeferredToBackgroundRetry() {
        when(product.restore(argThat(q -> q != null && q.orderId() == LOST))).thenThrow(new RuntimeException("系统繁忙，请稍后重试"));
        StockReconcileService.Report r = svc.reconcile(from, to);
        assertThat(r.restored()).containsExactly(PARTIAL);
        assertThat(r.deferred()).containsExactly(LOST);
        verify(orders).deferRestore(eq(LOST), anyString(), contains("系统繁忙"));
    }

    @Test
    void orderWithoutDeductAndDeductWithoutOrderRaiseAlerts() {
        long created = 200, noDeduct = 204, orphan = 301, archived = 305, nextWindow = 401;
        when(mapper.createdNormalBetween(eq("0"), eq(from), eq(to), anyInt())).thenReturn(List.of(created, noDeduct));
        // 宽窗口（±60s）：created 的流水落在窗口外侧也算有
        when(product.deducted(eq(from.minusSeconds(60).toEpochMilli()), eq(to.plusSeconds(60).toEpochMilli()), anyInt()))
                .thenReturn(Result.ok(List.of(created, orphan, archived, nextWindow)));
        when(product.deducted(eq(from.toEpochMilli()), eq(to.toEpochMilli()), anyInt()))
                .thenReturn(Result.ok(List.of(orphan, archived, nextWindow)));
        when(mapper.findOrder(anyString(), anyLong())).thenReturn(null);
        when(mapper.findOrder("archive", archived)).thenReturn(new OrderRow());
        when(mapper.findOrder("1", nextWindow)).thenReturn(new OrderRow()); // 订单 created_at 恰在窗口外

        StockReconcileService.Report r = svc.reconcile(from, to);
        assertThat(r.orderWithoutDeduct()).containsExactly(noDeduct);
        assertThat(r.deductWithoutOrder()).containsExactly(orphan);
        verify(ops).log(eq("ALERT"), contains("没有扣减流水"));
        verify(ops).log(eq("ALERT"), contains("找不到订单"));
        assertThat(r.clean()).isFalse();
    }

    @Test
    void cleanWindowReportsNothing() {
        when(product.ledgerByOrders(anyList())).thenReturn(Result.ok(List.of(
                new LedgerEntry(OK, 1L, "DEDUCT", 1), new LedgerEntry(OK, 1L, "RESTORE", 1),
                new LedgerEntry(PARTIAL, 1L, "DEDUCT", 2), new LedgerEntry(PARTIAL, 1L, "RESTORE", 2),
                new LedgerEntry(LOST, 5L, "DEDUCT", 4), new LedgerEntry(LOST, 5L, "RESTORE", 4))));
        StockReconcileService.Report r = svc.reconcile(from, to);
        assertThat(r.clean()).isTrue();
        verify(product, never()).restore(any());
        verify(ops, never()).log(anyString(), anyString());
    }
}
