package com.orangemall.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** 每次回调留痕（审计 + 排查重复回调）。列与 01-schema.sql 一致 */
@Data
@TableName("pay_notify_log")
public class PayNotifyLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String paymentNo;
    private String tradeStatus;
    private Integer amount;
    private boolean signOk;
    private String result;
    private Instant createdAt;
    private Instant updatedAt;
}
