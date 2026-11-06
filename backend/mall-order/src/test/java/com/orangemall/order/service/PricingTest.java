package com.orangemall.order.service;

import com.orangemall.api.dto.SkuInfo;
import com.orangemall.api.dto.UserCouponDTO;
import com.orangemall.order.domain.PriceDetail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 计价规则（单位分；与前端 Mock calcPrice 对齐）：满减 → 券（类目范围）→ 积分（≤20%，整元）→ 运费 → 最低 1 分 */
class PricingTest {

    static Pricing.Line line(int price, int count, int rootCategory) {
        return new Pricing.Line(new SkuInfo(1L, 1L, "商品", "默认", "🍊", "#f80", price, price, 99, "ON_SALE", "橙选自营",
                rootCategory, rootCategory), count);
    }

    static UserCouponDTO coupon(String type, int threshold, int amount, Integer scopeRoot) {
        return new UserCouponDTO(1L, "T1", "券", type, threshold, amount, scopeRoot == null ? "全品类" : "限定", scopeRoot, "UNUSED", null, null);
    }

    @ParameterizedTest(name = "商品 {0} 分 → 满减 {1}")
    @CsvSource({"29999, 0", "30000, 5000", "65000, 10000", "150000, 25000", "999900, 25000"})
    void fullReductionEvery300Minus50CappedAt5Times(int goods, int expected) {
        assertThat(Pricing.calc(List.of(line(goods, 1, 1)), null, 0, 0).price().fullReduction()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "商品 {0} 分 → 运费 {1}")
    @CsvSource({"9899, 1000", "9900, 0"})
    void freeFreightFrom99Yuan(int goods, int freight) {
        assertThat(Pricing.calc(List.of(line(goods, 1, 1)), null, 0, 0).price().freight()).isEqualTo(freight);
    }

    @Test
    void scopedCouponOnlyCountsMatchingRootCategory() {
        // 类目 1：200 元；类目 2：80 元。限类目 2、满 100 减 20 的券：可用金额 80 < 门槛 → 不可用
        List<Pricing.Line> lines = List.of(line(20000, 1, 1), line(8000, 1, 2));
        assertThat(Pricing.calc(lines, coupon("FULL_REDUCTION", 10000, 2000, 2), 0, 0).price().couponDeduct()).isZero();
        // 同样的券限类目 1：可用 200 元 ≥ 门槛 → 抵 20 元
        assertThat(Pricing.calc(lines, coupon("FULL_REDUCTION", 10000, 2000, 1), 0, 0).price().couponDeduct()).isEqualTo(2000);
    }

    @Test
    void discountCouponTakesPercentageOfEligibleAmount() {
        // 9 折券（amount=90）作用于 123.45 元 → 抵 12.35 元（四舍五入到分）
        assertThat(Pricing.calc(List.of(line(12345, 1, 1)), coupon("DISCOUNT", 0, 90, null), 0, 0).price().couponDeduct())
                .isEqualTo(1235);
    }

    @Test
    void pointsCappedAt20PercentAndBalanceInWholeYuan() {
        // 实付前 100 元：积分最多抵 20 元 = 2000 积分；想用 5000 积分 → 只用 2000
        Pricing.Calc c = Pricing.calc(List.of(line(10000, 1, 1)), null, 5000, 99999);
        assertThat(c.maxPoints()).isEqualTo(2000);
        assertThat(c.pointsUsed()).isEqualTo(2000);
        assertThat(c.price().pointsDeduct()).isEqualTo(2000);
        assertThat(c.price().payAmount()).isEqualTo(8000);
        // 余额 1550 积分 → 只能按整元用 1500
        Pricing.Calc low = Pricing.calc(List.of(line(10000, 1, 1)), null, 5000, 1550);
        assertThat(low.pointsUsed()).isEqualTo(1500);
        assertThat(low.price().pointsDeduct()).isEqualTo(1500);
    }

    @Test
    void payAmountIsAtLeastOneCent() {
        PriceDetail p = Pricing.calc(List.of(line(10000, 1, 1)), coupon("NO_THRESHOLD", 0, 50000, null), 0, 0).price();
        assertThat(p.couponDeduct()).isEqualTo(10000);
        assertThat(p.payAmount()).isEqualTo(1);
    }

    @Test
    void stackingOrderFullReductionThenCouponThenPoints() {
        // 2 × 180 元 = 360 元：满减 50 → 310；满 300 减 30 券（门槛按商品额）→ 280；积分 ≤ 56 元 → 用 5600
        Pricing.Calc c = Pricing.calc(List.of(line(18000, 2, 1)), coupon("FULL_REDUCTION", 30000, 3000, null), 100000, 100000);
        PriceDetail p = c.price();
        assertThat(p.goodsAmount()).isEqualTo(36000);
        assertThat(p.fullReduction()).isEqualTo(5000);
        assertThat(p.couponDeduct()).isEqualTo(3000);
        assertThat(p.pointsDeduct()).isEqualTo(5600);
        assertThat(p.payAmount()).isEqualTo(36000 - 5000 - 3000 - 5600);
    }
}
