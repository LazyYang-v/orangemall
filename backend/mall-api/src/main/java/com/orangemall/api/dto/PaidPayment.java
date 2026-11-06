package com.orangemall.api.dto;

import java.time.Instant;

/** 支付↔订单对账用：支付成功的流水摘要（mall-pay /inner/pay/paid → mall-order 对账任务） */
public record PaidPayment(Long orderId, String paymentNo, String channel, Instant paidAt) {
}
