package com.orangemall.api.dto;

/** 积分变动：change<0 时余额不足抛 8003 */
public record PointsChangeReq(Long userId, int change, String reason) {
}
