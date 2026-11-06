package com.orangemall.order.service;

import com.orangemall.api.client.MarketingInnerClient;
import com.orangemall.api.client.ProductInnerClient;
import com.orangemall.api.client.UserInnerClient;
import com.orangemall.common.Result;
import com.orangemall.order.domain.OrderItemRow;
import com.orangemall.order.domain.OrderRow;
import com.orangemall.order.domain.OrderStatus;
import com.orangemall.order.mapper.OrderMapper;
import com.orangemall.web.Notifier;
import com.orangemall.web.OpsLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 关单补偿只执行一次：MQ 延迟消息、DLQ 重驱动、每分钟超时扫描、用户取消可能对同一订单重复/并发到达，
 * 只有 CAS 真正把 PENDING_PAYMENT 改走的那次调用回补库存 / 退积分。
 *
 * <p>Mapper 用桩按 OrderMapper.xml#closeIfPending 的真实语义模拟（WHERE status='PENDING_PAYMENT' 时才改成传入状态），
 * 回归 Phase 4 发现的缺陷：close() 误把 PENDING_PAYMENT 当目标状态传入，订单永远停在待支付，
 * 超时扫描每分钟重复回补同一批订单的库存。
 */
class OrderCloseTest {
    private final OrderMapper mapper = mock(OrderMapper.class);
    private final ProductInnerClient product = mock(ProductInnerClient.class);
    private final UserInnerClient user = mock(UserInnerClient.class);
    private final MarketingInnerClient marketing = mock(MarketingInnerClient.class);
    private final OpsLogger ops = mock(OpsLogger.class);
    private final AtomicReference<String> status = new AtomicReference<>(OrderStatus.PENDING_PAYMENT);
    private OrderService service;
    private OrderRow order;

    /** 内存版 Redis 延迟集合（member → 到期时间）与重试计数 */
    private final java.util.Map<String, Long> retry = new java.util.HashMap<>();
    private final java.util.Map<Object, Long> attempts = new java.util.HashMap<>();

    @SuppressWarnings("unchecked")
    private StringRedisTemplate fakeRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ZSetOperations<String, String> z = mock(ZSetOperations.class);
        HashOperations<String, Object, Object> h = mock(HashOperations.class);
        when(redis.opsForZSet()).thenReturn(z);
        when(redis.opsForHash()).thenReturn(h);
        when(z.add(eq(OrderService.RESTORE_RETRY), anyString(), anyDouble()))
                .thenAnswer(inv -> retry.put(inv.getArgument(1), (long) (double) inv.getArgument(2)) == null);
        when(z.rangeByScore(eq(OrderService.RESTORE_RETRY), anyDouble(), anyDouble(), anyLong(), anyLong())).thenAnswer(inv -> {
            double max = inv.getArgument(2);
            return retry.entrySet().stream().filter(e -> e.getValue() <= max).map(java.util.Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        });
        when(z.remove(eq(OrderService.RESTORE_RETRY), any(Object[].class)))
                .thenAnswer(inv -> retry.remove((String) inv.getArgument(1)) == null ? 0L : 1L);
        when(h.increment(eq(OrderService.RESTORE_RETRY_N), any(), anyLong()))
                .thenAnswer(inv -> attempts.merge(inv.getArgument(1), (long) inv.getArgument(2), Long::sum));
        when(h.delete(eq(OrderService.RESTORE_RETRY_N), any(Object[].class)))
                .thenAnswer(inv -> attempts.remove(inv.getArgument(1)) == null ? 0L : 1L);
        return redis;
    }

    @BeforeEach
    void setUp() {
        service = new OrderService(mapper, mock(OrderAssembler.class), product, user, marketing,
                mock(RabbitTemplate.class), fakeRedis(), new com.fasterxml.jackson.databind.ObjectMapper(),
                ops, mock(Notifier.class), mock(org.springframework.transaction.support.TransactionTemplate.class));
        service.restoreBackoffMs = new long[]{0, 0};
        order = new OrderRow();
        order.setId(42L);
        order.setUserId(7L);
        order.setSrc("2");
        order.setSource("NORMAL");
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPointsUsed(300);

        when(mapper.closeIfPending(eq("2"), eq(42L), anyString(), any(), anyString())).thenAnswer(inv -> {
            String target = inv.getArgument(2);
            return status.compareAndSet(OrderStatus.PENDING_PAYMENT, target) && !OrderStatus.PENDING_PAYMENT.equals(target) ? 1 : 0;
        });
        OrderItemRow item = new OrderItemRow();
        item.setSkuId(100101L);
        item.setCount(2);
        when(mapper.findItems(eq("2"), anyList())).thenReturn(List.of(item));
        when(mapper.findOrder(anyString(), eq(42L))).thenReturn(order);
        when(product.restore(any())).thenReturn(Result.ok(1));
        when(user.changePoints(any())).thenReturn(Result.ok(0));
    }

    @Test
    void cancelMovesOrderOutOfPendingAndCompensatesOnce() {
        service.close(order, "用户主动取消", OrderStatus.CANCELLED);
        assertThat(status.get()).isEqualTo(OrderStatus.CANCELLED);

        // 之后超时扫描 / 延迟消息 / DLQ 再来：都不能再回补
        for (int i = 0; i < 5; i++) service.close(order, "支付超时，订单自动关闭", OrderStatus.CLOSED);

        verify(product, times(1)).restore(argThat(r -> r.orderId() == 42L));
        verify(user, times(1)).changePoints(any());
        assertThat(status.get()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void timeoutClosesWithClosedStatus() {
        service.close(order, "支付超时，订单自动关闭", OrderStatus.CLOSED);
        verify(mapper).closeIfPending(eq("2"), eq(42L), eq(OrderStatus.CLOSED), any(), anyString());
        assertThat(status.get()).isEqualTo(OrderStatus.CLOSED);
    }

    @Test
    void rejectsNonTerminalTargetStatus() {
        assertThatThrownBy(() -> service.close(order, "x", OrderStatus.PENDING_PAYMENT)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(product);
    }

    @Test
    void restoreIsRetriedUntilItSucceeds() {
        // 第一、二次：SKU 行被未决的下单全局事务占着全局锁（商品侧 @GlobalLock 等不到）；第三次成功
        when(product.restore(any())).thenThrow(new RuntimeException("Global lock wait timeout"))
                .thenThrow(new RuntimeException("Read timed out")).thenReturn(Result.ok(1));
        service.close(order, "用户主动取消", OrderStatus.CANCELLED);
        verify(product, times(3)).restore(argThat(r -> r.orderId() == 42L));
        verify(ops, never()).log(eq("ALERT"), anyString());
        verify(user, times(1)).changePoints(any());
    }

    @Test
    void syncFailureDefersToBackgroundRetryAndStillReleasesTheRest() {
        when(product.restore(any())).thenThrow(new RuntimeException("Global lock wait timeout"));
        service.close(order, "用户主动取消", OrderStatus.CANCELLED);
        verify(product, times(3)).restore(any());
        assertThat(retry).containsKey("42");
        verify(ops, never()).log(eq("ALERT"), anyString());
        verify(user, times(1)).changePoints(any());
        assertThat(status.get()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void backgroundRetryRestoresOnceTheLockIsFree() {
        // 同步 3 次都失败（TC 故障切换中），后台第 2 次时孤儿事务已回滚、全局锁释放
        when(product.restore(any())).thenThrow(new RuntimeException("Global lock wait timeout"))
                .thenThrow(new RuntimeException("Global lock wait timeout"))
                .thenThrow(new RuntimeException("Global lock wait timeout"))
                .thenThrow(new RuntimeException("系统繁忙，请稍后重试"))
                .thenReturn(Result.ok(1));
        service.close(order, "用户主动取消", OrderStatus.CANCELLED);

        assertThat(service.retryPendingRestores(retry.get("42") - 1)).isZero(); // 未到期不动
        assertThat(service.retryPendingRestores(retry.get("42"))).isEqualTo(1);  // 第 1 次后台重试失败 → 重新排期
        assertThat(retry).containsKey("42");
        assertThat(service.retryPendingRestores(retry.get("42"))).isEqualTo(1);  // 第 2 次成功
        assertThat(retry).isEmpty();
        assertThat(attempts).isEmpty();
        verify(product, times(5)).restore(argThat(r -> r.orderId() == 42L && r.items().get(0).count() == 2));
        verify(ops, never()).log(eq("ALERT"), anyString());
    }

    @Test
    void exhaustedBackgroundRetryRaisesAlert() {
        when(product.restore(any())).thenThrow(new RuntimeException("Global lock wait timeout"));
        service.close(order, "用户主动取消", OrderStatus.CANCELLED);
        for (int i = 0; i < service.restoreRetryDelayMs.length; i++) {
            verify(ops, never()).log(eq("ALERT"), anyString());
            service.retryPendingRestores(retry.get("42"));
        }
        verify(product, times(3 + service.restoreRetryDelayMs.length)).restore(any());
        verify(ops).log(eq("ALERT"), contains("库存回补重试耗尽"));
        assertThat(retry).isEmpty();
        assertThat(attempts).isEmpty();
    }
}
