package com.orangemall.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** 支付流水（mall_pay.payment）。status: PENDING / SUCCESS / FAILED / CLOSED(订单已关仍到账，转退款) */
@Data
@TableName("payment")
public class Payment {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String paymentNo;
    private Long orderId;
    private Long userId;
    private String channel;
    private int amount;
    private String status;
    private String qrCode;
    private Instant expireAt;
    private int callbackCount;
    private Instant paidAt;
    /** 关联退款流水号（refund_order.id；售后退款后写入） */
    private String refundNo;
    private int refundedAmount;
    private Instant createdAt;
    private Instant updatedAt;
}
