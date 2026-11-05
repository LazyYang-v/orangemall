package com.orangemall.marketing.dto;

import com.orangemall.api.dto.UserCouponDTO;
import com.orangemall.marketing.entity.CouponTemplate;
import com.orangemall.marketing.entity.UserCoupon;

import java.time.Instant;

/** 对外 DTO（字段名与 frontend/src/types/domain.ts 一致） */
public final class Dtos {
    private Dtos() {
    }

    /** CouponTemplate；receivedByMe 为 null 时不输出（后台列表） */
    public record CouponTemplateVO(String id, String title, String type, int threshold, int amount, String scope,
                                   int total, int received, int validDays, int perUserLimit, Instant startAt,
                                   Instant endAt, Boolean receivedByMe) {
        public static CouponTemplateVO of(CouponTemplate t, Boolean receivedByMe) {
            return new CouponTemplateVO(t.getId(), t.getTitle(), t.getType(), t.getThreshold(), t.getAmount(), t.getScope(),
                    t.getTotal(), t.getReceived(), t.getValidDays(), t.getPerUserLimit(), t.getStartAt(), t.getEndAt(),
                    receivedByMe);
        }
    }

    public record UserCouponVO(Long id, String templateId, String title, String type, int threshold, int amount,
                               String scope, String status, Instant validFrom, Instant validTo) {
        public static UserCouponVO of(UserCoupon c) {
            return new UserCouponVO(c.getId(), c.getTemplateId(), c.getTitle(), c.getType(), c.getThreshold(), c.getAmount(),
                    c.getScope(), c.getStatus(), c.getValidFrom(), c.getValidTo());
        }
    }

    public static UserCouponDTO toDto(UserCoupon c) {
        return new UserCouponDTO(c.getId(), c.getTemplateId(), c.getTitle(), c.getType(), c.getThreshold(), c.getAmount(),
                c.getScope(), c.getScopeRootId(), c.getStatus(), c.getValidFrom(), c.getValidTo());
    }

    /** 后台新建券模板（Partial&lt;CouponTemplate&gt;；scopeRootId 可选，不传则按 scope 文案推断根类目） */
    public record CreateTemplateReq(String title, String type, Integer threshold, Integer amount, String scope,
                                    Integer scopeRootId, Integer total, Integer validDays, Integer perUserLimit,
                                    Instant startAt, Instant endAt) {
    }
}
