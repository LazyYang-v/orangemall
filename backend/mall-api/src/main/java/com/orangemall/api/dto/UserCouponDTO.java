package com.orangemall.api.dto;

import java.time.Instant;

/** 用户券（营销服务 → 订单服务试算/下单）。scopeRootId 为 null 表示全品类可用 */
public record UserCouponDTO(Long id, String templateId, String title, String type, int threshold, int amount,
                            String scope, Integer scopeRootId, String status, Instant validFrom, Instant validTo) {
}
