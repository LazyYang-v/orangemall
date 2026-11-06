package com.orangemall.pay.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orangemall.pay.entity.PayNotifyLog;
import com.orangemall.pay.mapper.NotifyLogMapper;
import com.orangemall.pay.service.RefundService;
import com.orangemall.web.OpsLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 支付回调审计日志保留策略（OM-010）：每天 03:40 清理 30 天前的记录，防止 pay_notify_log 无限增长。
 * 售后对账（refund-design §4）：REFUNDING 滞留告警。
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
}
