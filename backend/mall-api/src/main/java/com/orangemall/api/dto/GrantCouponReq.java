package com.orangemall.api.dto;

/** 发券：注册送新人券、积分兑换券。amount/title 不为空时覆盖模板值（如积分兑换 5 元券） */
public record GrantCouponReq(Long userId, String templateId, Integer amount, String title) {
}
