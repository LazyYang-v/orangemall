package com.orangemall.api.dto;

/** 锁券 / 释放 / 核销：UNUSED→LOCKED（下单）、LOCKED→UNUSED（关单）、LOCKED→USED（确认收货） */
public record CouponOpReq(Long userId, Long couponId, Long orderId) {
}
