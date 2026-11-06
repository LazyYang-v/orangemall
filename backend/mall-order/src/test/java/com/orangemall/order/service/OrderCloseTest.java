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
import org.springframework.data.redis.core.StringRedisTemplate;

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
    private final AtomicReference<String> status = new AtomicReference<>(OrderStatus.PENDING_PAYMENT);
    private OrderService service;
    private OrderRow order;

    @BeforeEach
    void setUp() {
        service = new OrderService(mapper, mock(OrderAssembler.class), product, user, marketing,
                mock(RabbitTemplate.class), mock(StringRedisTemplate.class), new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(OpsLogger.class), mock(Notifier.class));
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
        when(product.restore(anyList())).thenReturn(Result.ok());
        when(user.changePoints(any())).thenReturn(Result.ok(0));
    }

    @Test
    void cancelMovesOrderOutOfPendingAndCompensatesOnce() {
        service.close(order, "用户主动取消", OrderStatus.CANCELLED);
        assertThat(status.get()).isEqualTo(OrderStatus.CANCELLED);

        // 之后超时扫描 / 延迟消息 / DLQ 再来：都不能再回补
        for (int i = 0; i < 5; i++) service.close(order, "支付超时，订单自动关闭", OrderStatus.CLOSED);

        verify(product, times(1)).restore(anyList());
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
}
