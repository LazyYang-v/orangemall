package com.orangemall.api.dto;

/** 秒杀落单（秒杀消费者 → 订单服务）。orderId 由秒杀入队时预生成（带用户基因） */
public record SeckillOrderReq(Long orderId, Long userId, String activityId, Long skuId, int seckillPrice, Long addressId) {
}
