package com.orangemall.api.dto;

/** 延迟关单消息体（RabbitMQ TTL + DLX） */
public record OrderCloseMsg(Long orderId, Long userId) {
}
