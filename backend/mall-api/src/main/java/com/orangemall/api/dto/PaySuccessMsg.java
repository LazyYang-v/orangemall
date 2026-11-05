package com.orangemall.api.dto;

import java.time.Instant;

/** RabbitMQ pay.exchange / pay.success 消息体 */
public record PaySuccessMsg(Long orderId, String paymentNo, String channel, Instant paidAt) {
}
