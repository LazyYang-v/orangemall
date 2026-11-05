package com.orangemall.order.service;

import com.orangemall.api.dto.SkuInfo;
import com.orangemall.api.dto.UserCouponDTO;
import com.orangemall.order.domain.PriceDetail;

import java.util.List;

/**
 * 计价规则（与 Mock calcPrice 逐行对应，单位分）：
 * 跨店每满 300 减 50（最多 5 次）→ 优惠券（受 scopeRootId 类目限制）→ 积分（100 积分 = 1 元，最多抵扣 20%，按整元）
 * → 运费（满 99 包邮，否则 10 元）→ 应付最低 1 分。
 */
public final class Pricing {
    public static final int FULL_THRESHOLD = 30000;
    public static final int FULL_REDUCE = 5000;
    public static final int FULL_MAX_TIMES = 5;
    public static final int POINTS_RATE = 100;
    public static final double MAX_POINTS_RATIO = 0.2;
    public static final int FREE_FREIGHT = 9900;
    public static final int FREIGHT = 1000;

    /** 结算行：SKU 快照 + 数量 */
    public record Line(SkuInfo sku, int count) {
        int amount() {
            return sku.price() * count;
        }
    }

    public record Calc(PriceDetail price, int pointsUsed, int maxPoints) {
    }

    private Pricing() {
    }

    public static int goodsAmount(List<Line> lines) {
        return lines.stream().mapToInt(Line::amount).sum();
    }

    /** 券可用的商品金额：scopeRootId 为空 = 全品类，否则只算根类目匹配的商品 */
    public static int eligibleAmount(List<Line> lines, UserCouponDTO c) {
        Integer root = c.scopeRootId();
        return lines.stream().filter(l -> root == null || root.equals(l.sku().rootCategoryId())).mapToInt(Line::amount).sum();
    }

    public static int couponDeduct(UserCouponDTO c, int eligible) {
        if (eligible < c.threshold() || eligible <= 0) return 0;
        if ("DISCOUNT".equals(c.type())) return (int) Math.round(eligible * (100 - c.amount()) / 100.0);
        return Math.min(c.amount(), eligible);
    }

    public static Calc calc(List<Line> lines, UserCouponDTO coupon, int pointsUsed, int pointsBalance) {
        int goods = goodsAmount(lines);
        int fullReduction = Math.min(FULL_MAX_TIMES, goods / FULL_THRESHOLD) * FULL_REDUCE;
        int couponDeduct = coupon == null ? 0 : couponDeduct(coupon, eligibleAmount(lines, coupon));
        int freight = goods >= FREE_FREIGHT ? 0 : FREIGHT;
        int afterDiscount = Math.max(0, goods - fullReduction - couponDeduct);
        // 积分上限：实付的 20%，按整元取整后换算成积分（afterDiscount * 0.2 / 100 元 → floor → × 100 积分）
        int maxPoints = (afterDiscount / 500) * POINTS_RATE;
        int usable = Math.max(0, Math.min(Math.min(pointsUsed, maxPoints), (Math.max(0, pointsBalance) / POINTS_RATE) * POINTS_RATE));
        int pointsDeduct = (usable / POINTS_RATE) * 100; // 100 积分 = 100 分
        PriceDetail price = new PriceDetail(goods, freight, fullReduction, couponDeduct, pointsDeduct, 0,
                Math.max(1, afterDiscount - pointsDeduct + freight));
        return new Calc(price, (pointsDeduct / 100) * POINTS_RATE, maxPoints);
    }
}
