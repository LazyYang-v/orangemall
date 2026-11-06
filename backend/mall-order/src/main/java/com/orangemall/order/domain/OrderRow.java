package com.orangemall.order.domain;

import lombok.Data;

import java.time.Instant;

/** t_order_{0..3} / t_order_archive 的一行。src = 来源表后缀（查询时由 SQL 填充：0..3 / archive） */
@Data
public class OrderRow {
    private Long id;
    private Long userId;
    private String status;
    private String source;
    private int goodsAmount;
    private int freight;
    private int fullReduction;
    private int couponDeduct;
    private int pointsDeduct;
    private int seckillDeduct;
    private int payAmount;
    private Long couponId;
    private int pointsUsed;
    private String remark;
    private String addressJson;
    /** 冗余列（OM-015）：收货人/电话快照，避免 admin 关键字检索 JSON_EXTRACT 全表扫 */
    private String receiver;
    private String phone;
    private String activityId;
    private Instant payDeadline;
    private Instant paidAt;
    private Instant shippedAt;
    private Instant completedAt;
    private Instant closedAt;
    private String closeReason;
    private String payChannel;
    private String paymentNo;
    private String logisticsJson;
    private Instant createdAt;
    private Instant updatedAt;
    private String src;
}
