package com.orangemall.api.dto;

import java.time.Instant;

/** 订单摘要（订单服务 → 支付服务） */
public record OrderBrief(Long id, Long userId, String status, int payAmount, Instant payDeadline) {
}
