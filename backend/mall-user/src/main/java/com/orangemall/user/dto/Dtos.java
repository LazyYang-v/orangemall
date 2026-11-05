package com.orangemall.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.orangemall.user.entity.Address;
import com.orangemall.user.entity.PointsGoods;
import com.orangemall.user.entity.PointsRecord;
import com.orangemall.user.entity.User;

import java.time.Instant;
import java.util.List;

/** 对外 DTO（字段名与 frontend/src/types/domain.ts 一致） */
public final class Dtos {
    private Dtos() {
    }

    public record LoginReq(String phone, String password, String smsCode) {
    }

    public record RegisterReq(String phone, String password, String nickname, String smsCode) {
    }

    public record SmsReq(String phone) {
    }

    public record LoginResp(String token, UserProfile user) {
    }

    public record SmsResp(boolean sent, String hint) {
    }

    public record UserProfile(Long id, String nickname, String phone, String avatar, int level, int points,
                              int creditScore, String role) {
        public static UserProfile of(User u) {
            return new UserProfile(u.getId(), u.getNickname(), u.getPhone(), u.getAvatar(), u.getLevel(), u.getPoints(),
                    u.getCreditScore(), u.getRole());
        }
    }

    /** 收货地址：请求体（Partial，有 id 为更新）与响应共用 */
    public record AddressVO(Long id, String receiver, String phone, String province, String city, String district,
                            String detail, @JsonProperty("isDefault") Boolean isDefault) {
        public static AddressVO of(Address a) {
            return new AddressVO(a.getId(), a.getReceiver(), a.getPhone(), a.getProvince(), a.getCity(), a.getDistrict(),
                    a.getDetail(), Boolean.TRUE.equals(a.getIsDefault()));
        }
    }

    public record PointsRecordVO(Long id, int change, String reason, Instant createdAt) {
        public static PointsRecordVO of(PointsRecord r) {
            return new PointsRecordVO(r.getId(), r.getChangeAmount(), r.getReason(), r.getCreatedAt());
        }
    }

    public record PointsVO(int balance, List<PointsRecordVO> records) {
    }

    public record PointsGoodsVO(String id, String name, String art, String tint, int points, int stock) {
        public static PointsGoodsVO of(PointsGoods g) {
            return new PointsGoodsVO(g.getId(), g.getName(), g.getArt(), g.getTint(), g.getPoints(), g.getStock());
        }
    }

    public record BalanceVO(int balance) {
    }

    public record RiskUserVO(Long userId, String nickname, int creditScore, int hits, String lastAction, boolean blocked) {
        public static RiskUserVO of(User u) {
            return new RiskUserVO(u.getId(), u.getNickname(), u.getCreditScore(), u.getRiskHits(), u.getLastAction(),
                    Boolean.TRUE.equals(u.getBlocked()));
        }
    }

    public record BlockReq(Boolean blocked) {
    }
}
