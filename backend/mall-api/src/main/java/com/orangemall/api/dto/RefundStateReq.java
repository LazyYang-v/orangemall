package com.orangemall.api.dto;

/**
 * 订单售后状态流转请求（支付服务 → 订单服务）。
 * from 为空表示仅校验 to 合法性（用于回退），from 非空时需与当前状态一致（CAS）。
 */
public record RefundStateReq(String from, String to) {
}
