package com.orangemall.api.dto;

public record UserBrief(Long id, String nickname, String phone, int points, int creditScore, boolean blocked, String role) {
}
