package com.orangemall.pay.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orangemall.pay.entity.PayNotifyLog;
import com.orangemall.pay.mapper.NotifyLogMapper;
import com.orangemall.pay.service.RefundService;
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
 * 支付回调审计日志保留策略（OM-010）：每天 03:40 清理 30 天前的记录，防止 pay_notify_log 无限增长。
 * 售后对账（refund-design §4）：REFUNDING 滞留告警；DONE 之后回补库存 / 订单 REFUNDED 未完成的定时重放（Phase 6）。
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class PayJobs {
    static final int RETENTION_DAYS = 30;

    private final NotifyLogMapper notifyLogMapper;
    private final RefundService refunds;
    private final OpsLogger ops;
    private final StringRedisTemplate redis;

    static final Duration SETTLE_GRACE = Duration.ofSeconds(30);
    static final Duration SETTLE_ALERT_AFTER = Duration.ofMinutes(10);

    @Scheduled(cron = "0 40 3 * * *", zone = "Asia/Shanghai")
    public void cleanupNotifyLog() {
        int deleted = notifyLogMapper.delete(new LambdaQueryWrapper<PayNotifyLog>()
                .lt(PayNotifyLog::getCreatedAt, Instant.now().minus(Duration.ofDays(RETENTION_DAYS))));
        ops.log("PAY", "[清理] 支付回调日志保留 " + RETENTION_DAYS + " 天，本次清理 " + deleted + " 条");
        if (deleted > 0) log.info("pay_notify_log cleaned: {}", deleted);
    }

    /** 售后对账告警（docs/refund-design.md §4）：REFUNDING 停留超过 24h 需人工跟进 */
    @Scheduled(cron = "0 10 9-22 * * *", zone = "Asia/Shanghai")
    public void alertStuckRefunds() {
        List<com.orangemall.pay.entity.RefundOrder> stuck =
                refunds.stuckRefunding(Duration.ofHours(24));
        for (com.orangemall.pay.entity.RefundOrder r : stuck) {
            ops.log("REFUND", "[告警] 售后单 " + r.getId() + " 订单 " + r.getOrderId()
                    + " REFUNDING 超过 24h（渠道回调未达），需人工核查");
        }
    }

    /**
     * 退款完成补偿：DONE 超过 30s 仍未 settle（回补库存 / 订单 REFUNDED 失败）的售后单重放 settle（两步都幂等）。
     * 每分钟一轮，Redis SETNX 多实例互斥；DONE 超过 10 分钟仍失败的告警一次（同一单 24h 内不重复）。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 40_000)
    public void settleDoneRefunds() {
        Boolean got = redis.opsForValue().setIfAbsent("pay:refund:settle:lock", "1", Duration.ofSeconds(50));
        if (!Boolean.TRUE.equals(got)) return;
        try {
            for (com.orangemall.pay.entity.RefundOrder r : refunds.unsettled(SETTLE_GRACE, 100)) {
                try {
                    refunds.settle(r);
                    ops.log("REFUND", "[补偿] 售后单 " + r.getId() + " 订单 " + r.getOrderId() + " 退款收尾已补齐");
                } catch (Exception e) {
                    boolean old = r.getDoneAt() != null && r.getDoneAt().isBefore(Instant.now().minus(SETTLE_ALERT_AFTER));
                    if (old && Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("pay:refund:settle:alerted:" + r.getId(), "1", Duration.ofHours(24))))
                        ops.log("ALERT", "[退款收尾失败] 售后单 " + r.getId() + " 订单 " + r.getOrderId()
                                + " 已退款超过 10 分钟，回补库存 / 订单状态仍未完成，需人工核查：" + e.getMessage());
                    log.warn("settle refund {} failed: {}", r.getId(), e.getMessage());
                }
            }
        } finally {
            redis.delete("pay:refund:settle:lock");
        }
    }
}
