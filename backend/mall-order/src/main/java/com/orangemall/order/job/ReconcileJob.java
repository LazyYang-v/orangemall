package com.orangemall.order.job;

import com.orangemall.api.client.PayInnerClient;
import com.orangemall.api.dto.PaidPayment;
import com.orangemall.api.dto.PaySuccessMsg;
import com.orangemall.common.Const;
import com.orangemall.order.domain.OrderRow;
import com.orangemall.order.domain.OrderStatus;
import com.orangemall.order.mapper.OrderMapper;
import com.orangemall.order.service.OrderService;
import com.orangemall.web.OpsLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 支付↔订单对账兜底（OM-003）：pay.success 消息丢失/消费失败时，订单会停在 PENDING_PAYMENT。
 * 定时（5 分钟，Redis SETNX 多实例互斥）从 mall-pay 拉取新增「支付成功」流水，
 * 核对本库订单状态；若仍为 PENDING_PAYMENT 则直接驱动 onPaySuccess（幂等 CAS）。
 * 检查点存 Redis；首次启动从 15 分钟前开始，最多回补一轮，避免面覆盖面过大。
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class ReconcileJob {
    private static final String CKPT = "order:reconcile:since";
    private static final long MAX_BACK_SEC = 24 * 3600;

    private final PayInnerClient payInner;
    private final OrderMapper orderMapper;
    private final OrderService orders;
    private final StringRedisTemplate redis;
    private final OpsLogger ops;

    @Scheduled(fixedDelay = 300_000, initialDelay = 45_000)
    public void reconcile() {
        Boolean got = redis.opsForValue().setIfAbsent(CKPT + ":lock", "1", Duration.ofSeconds(240));
        if (!Boolean.TRUE.equals(got)) return;
        try {
            String raw = redis.opsForValue().get(CKPT);
            long sinceRaw = raw == null ? 0 : Long.parseLong(raw);
            long since = sinceRaw == 0 ? Instant.now().getEpochSecond() - 900 : sinceRaw;
            long next = since;
            List<PaidPayment> paid = payInner.paidSince(since).unwrap();
            int fixed = 0;
            for (PaidPayment p : paid) {
                next = Math.max(next, p.paidAt().getEpochSecond());
                OrderRow o = orderMapper.findOrder(com.orangemall.order.domain.Shard.of(p.orderId()), p.orderId());
                if (o == null) continue; // 秒杀等场景，暂不处理已归档订单
                if (!OrderStatus.PENDING_PAYMENT.equals(o.getStatus())) continue;
                ops.log("PAY", "[对账] 订单 " + p.orderId() + " 支付已成功但状态为 PENDING_PAYMENT，触发补偿标记");
                orders.onPaySuccess(new PaySuccessMsg(p.orderId(), p.paymentNo(), p.channel(), p.paidAt()));
                fixed++;
            }
            long now = Instant.now().getEpochSecond();
            long checkpoint = Math.min(now - 60, Math.max(next, since));
            long lower = Instant.now().getEpochSecond() - MAX_BACK_SEC;
            redis.opsForValue().set(CKPT, String.valueOf(Math.max(checkpoint, lower)));
            if (fixed > 0) log.info("reconcile fixed {} orders since {}", fixed, since);
        } catch (Exception e) {
            log.warn("reconcile failed: {}", e.getMessage());
        } finally {
            redis.delete(CKPT + ":lock");
        }
    }
}
