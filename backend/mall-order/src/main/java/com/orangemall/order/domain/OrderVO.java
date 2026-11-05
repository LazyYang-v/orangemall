package com.orangemall.order.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

/** 与前端 Order 一致（null 字段不输出） */
public record OrderVO(Long id, Long userId, String status, String source, List<Item> items, PriceDetail price,
                      JsonNode address, Long couponId, int pointsUsed, String remark, Instant createdAt,
                      Instant payDeadline, Instant paidAt, Instant shippedAt, Instant completedAt, Instant closedAt,
                      String closeReason, String payChannel, String paymentNo, JsonNode logistics) {

    /** 与前端 OrderItem 一致 */
    public record Item(Long skuId, Long spuId, String name, String specText, String art, String tint, int price, int count) {
    }
}
