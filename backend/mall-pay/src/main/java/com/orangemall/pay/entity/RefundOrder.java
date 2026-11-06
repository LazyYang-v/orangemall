package com.orangemall.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 售后/退款单（mall_pay.refund_order，docs/refund-design.md）。
 * status: WAIT_APPROVAL → APPROVED → REFUNDING → DONE / REJECTED / CANCELLED。
 * 幂等键 = id（回调重复、重复审批不产生二次退款）；同一订单仅一笔进行中由业务校验保证。
 */
@Data
@TableName("refund_order")
public class RefundOrder {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long orderId;
    private Long userId;
    private String paymentNo;
    private String type;
    private String reason;
    private int amount;
    private String status;
    private String prevStatus;
    private String auditRemark;
    private Long auditBy;
    private Instant auditAt;
    private String refundTradeNo;
    private Instant doneAt;
    private Instant createdAt;
    private Instant updatedAt;
}
