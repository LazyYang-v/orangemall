package com.orangemall.api.dto;

/** Kafka seckill-order 消息体 */
public record SeckillMsg(Long orderId, Long userId, String activityId, Long skuId, int seckillPrice, Long addressId) {
}
