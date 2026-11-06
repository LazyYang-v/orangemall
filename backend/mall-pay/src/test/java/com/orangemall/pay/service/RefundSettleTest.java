package com.orangemall.pay.service;

import com.orangemall.api.client.OrderInnerClient;
import com.orangemall.api.client.ProductInnerClient;
import com.orangemall.api.dto.StockItem;
import com.orangemall.common.Result;
import com.orangemall.pay.entity.RefundOrder;
import com.orangemall.pay.job.PayJobs;
import com.orangemall.pay.mapper.NotifyLogMapper;
import com.orangemall.pay.mapper.PayMapper;
import com.orangemall.pay.mapper.RefundMapper;
import com.orangemall.web.Notifier;
import com.orangemall.web.OpsLogger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 退款完成收尾可补偿（Phase 6）：售后单 CAS 成 DONE 之后，回补库存 / 订单 REFUNDED 失败不再丢 ——
 * 回调照常返回（DONE 已落库，重放回调只会被幂等忽略），settled_at 留空，PayJobs 定时重放 settle。
 * RefundMapper 的 update 桩：第 1 次 = DONE CAS，之后 = settled_at CAS。
 */
class RefundSettleTest {
    private final RefundMapper refunds = mock(RefundMapper.class);
    private final OrderInnerClient orders = mock(OrderInnerClient.class);
    private final ProductInnerClient product = mock(ProductInnerClient.class);
    private final OpsLogger ops = mock(OpsLogger.class);
    private final RefundService service = new RefundService(refunds, mock(PayMapper.class), orders, product,
            mock(Notifier.class), ops, mock(SandboxSigner.class));
    private RefundOrder r;

    @BeforeAll
    static void lambdaCache() { // 不起 Spring：手动登记实体元数据，LambdaUpdateWrapper 才能解析列名
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, RefundOrder.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, com.orangemall.pay.entity.Payment.class);
    }

    @BeforeEach
    void setUp() {
        r = new RefundOrder();
        r.setId(9L);
        r.setOrderId(42L);
        r.setUserId(7L);
        r.setType("REFUND");
        r.setStatus("REFUNDING");
        r.setAmount(1999);
        when(refunds.selectById(9L)).thenReturn(r);
        when(refunds.update(isNull(), any())).thenReturn(1);
        when(orders.items(42L)).thenReturn(Result.ok(List.of(new StockItem(100101L, 2))));
        when(product.restore(any())).thenReturn(Result.ok(1));
        when(orders.refundState(eq(42L), any())).thenReturn(Result.ok(true));
    }

    @Test
    void happyPathRestoresStockMarksOrderAndSettles() {
        service.applyRefundSuccess(9L, null, 1999);
        verify(product).restore(argThat(q -> q.orderId() == 42L));
        verify(orders).refundState(eq(42L), argThat(q -> "REFUNDED".equals(q.to())));
        verify(refunds, times(2)).update(isNull(), any()); // DONE CAS + settled_at
        verify(ops).log(eq("REFUND"), contains("[完成]"));
    }

    @Test
    void failedRestoreDoesNotThrowAndLeavesRefundUnsettled() {
        when(product.restore(any())).thenThrow(new RuntimeException("系统繁忙，请稍后重试"));
        service.applyRefundSuccess(9L, null, 1999); // 不抛：渠道回调照常确认
        verify(refunds, times(1)).update(isNull(), any()); // 只有 DONE CAS，settled_at 未写
        verify(orders, never()).refundState(anyLong(), any());
        verify(ops).log(eq("REFUND"), contains("[完成延后]"));
    }

    @Test
    void replayAfterPartialFailureCompletesIdempotently() {
        // 第一次：回补成功、订单改状态失败；重放：回补再调一次（商品侧按流水幂等）、订单改状态成功
        when(orders.refundState(eq(42L), any())).thenThrow(new RuntimeException("Read timed out")).thenReturn(Result.ok(false));
        service.applyRefundSuccess(9L, null, 1999);
        verify(refunds, times(1)).update(isNull(), any());

        r.setStatus("DONE");
        service.settle(r);
        verify(product, times(2)).restore(argThat(q -> q.orderId() == 42L));
        verify(refunds, times(2)).update(isNull(), any());
        verify(ops).log(eq("REFUND"), contains("[完成]"));
    }

    @Test
    void exchangeDoesNotRestoreStock() {
        r.setType("EXCHANGE");
        service.applyRefundSuccess(9L, null, 1999);
        verifyNoInteractions(product);
        verify(orders).refundState(eq(42L), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void jobReplaysUnsettledAndAlertsOnceWhenStuckOverTenMinutes() {
        RefundService svc = mock(RefundService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> v = mock(ValueOperations.class);
        Set<String> keys = new HashSet<>();
        when(redis.opsForValue()).thenReturn(v);
        when(v.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(inv -> keys.add(inv.getArgument(0)));
        when(redis.delete(anyString())).thenAnswer(inv -> keys.remove((String) inv.getArgument(0)));
        PayJobs jobs = new PayJobs(mock(NotifyLogMapper.class), svc, ops, redis);

        RefundOrder fresh = new RefundOrder(), stuck = new RefundOrder();
        fresh.setId(1L);
        fresh.setDoneAt(Instant.now().minusSeconds(60));
        stuck.setId(2L);
        stuck.setDoneAt(Instant.now().minus(Duration.ofMinutes(11)));
        when(svc.unsettled(any(), anyInt())).thenReturn(List.of(fresh, stuck));
        doThrow(new RuntimeException("mall-order 不可用")).when(svc).settle(any());

        jobs.settleDoneRefunds();
        jobs.settleDoneRefunds();
        verify(svc, times(4)).settle(any());
        verify(ops, times(1)).log(eq("ALERT"), contains("售后单 2"));
        verify(ops, never()).log(eq("ALERT"), contains("售后单 1 "));
        assertThat(keys).doesNotContain("pay:refund:settle:lock");
    }
}
