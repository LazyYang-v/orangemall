package com.orangemall.marketing.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.orangemall.api.dto.CouponOpReq;
import com.orangemall.api.dto.GrantCouponReq;
import com.orangemall.common.BizException;
import com.orangemall.common.IdGenerator;
import com.orangemall.marketing.dto.Dtos.CouponTemplateVO;
import com.orangemall.marketing.dto.Dtos.CreateTemplateReq;
import com.orangemall.marketing.entity.CouponTemplate;
import com.orangemall.marketing.entity.UserCoupon;
import com.orangemall.marketing.mapper.CouponTemplateMapper;
import com.orangemall.marketing.mapper.UserCouponMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {
    private static final Set<String> TYPES = Set.of("FULL_REDUCTION", "DISCOUNT", "NO_THRESHOLD");
    /** 根类目名 → ID（与 mall_product.category 种子一致），用于后台新建券时按 scope 文案推断适用品类 */
    private static final Map<String, Integer> ROOT_CATEGORIES = new LinkedHashMap<>();

    static {
        String[] names = {"数码家电", "服饰鞋包", "家居生活", "美妆个护", "食品生鲜", "母婴玩具", "运动户外", "汽车用品", "图书文娱", "医药保健"};
        for (int i = 0; i < names.length; i++) ROOT_CATEGORIES.put(names[i], i + 1);
    }

    private final CouponTemplateMapper templateMapper;
    private final UserCouponMapper userCouponMapper;
    private final CouponStockCache cache;
    private final CouponWriter writer;

    /* ---------------- 模板 ---------------- */

    /** 新建的在前，种子按 id 升序（与 Mock unshift 顺序一致） */
    public List<CouponTemplate> allTemplates() {
        return templateMapper.selectList(Wrappers.<CouponTemplate>lambdaQuery()
                .orderByDesc(CouponTemplate::getCreatedAt).orderByAsc(CouponTemplate::getId));
    }

    /** receivedByMe = 我已领张数 ≥ 每人限领（未登录恒为 false） */
    public List<CouponTemplateVO> templates(Long userId, int limit) {
        Map<String, Integer> mine = new HashMap<>();
        if (userId != null) {
            for (Map<String, Object> row : userCouponMapper.countByTemplate(userId)) {
                mine.put(String.valueOf(row.get("k")), ((Number) row.get("c")).intValue());
            }
        }
        return allTemplates().stream().limit(limit)
                .map(t -> CouponTemplateVO.of(t, mine.getOrDefault(t.getId(), 0) >= t.getPerUserLimit()))
                .toList();
    }

    public CouponTemplateVO create(CreateTemplateReq b) {
        if (b == null || b.title() == null || b.title().isBlank()) throw BizException.of(400, "请填写券名称");
        String type = b.type() == null ? "FULL_REDUCTION" : b.type();
        if (!TYPES.contains(type)) throw BizException.of(400, "券类型不正确");
        if (b.amount() == null || b.amount() <= 0) throw BizException.of(400, "请填写券面额");
        if (b.total() == null || b.total() <= 0) throw BizException.of(400, "请填写发行量");
        Instant now = Instant.now();
        CouponTemplate t = new CouponTemplate();
        t.setId(newTemplateId());
        t.setTitle(b.title().trim());
        t.setType(type);
        t.setThreshold("NO_THRESHOLD".equals(type) || b.threshold() == null ? 0 : Math.max(0, b.threshold()));
        t.setAmount(b.amount());
        t.setScope(b.scope() == null || b.scope().isBlank() ? "全场通用" : b.scope());
        t.setScopeRootId(b.scopeRootId() != null ? b.scopeRootId() : inferRoot(t.getScope()));
        t.setTotal(b.total());
        t.setReceived(0);
        t.setValidDays(b.validDays() == null || b.validDays() <= 0 ? 7 : b.validDays());
        t.setPerUserLimit(b.perUserLimit() == null || b.perUserLimit() <= 0 ? 1 : b.perUserLimit());
        t.setStartAt(b.startAt() == null ? now : b.startAt());
        t.setEndAt(b.endAt() == null ? now.plus(Duration.ofDays(30)) : b.endAt());
        templateMapper.insert(t);
        cache.warm(t);
        log.info("coupon template created {} {}", t.getId(), t.getTitle());
        return CouponTemplateVO.of(t, null);
    }

    private String newTemplateId() {
        for (int i = 0; i < 5; i++) {
            String s = String.valueOf(IdGenerator.nextId());
            String id = "CT" + s.substring(s.length() - 6);
            if (templateMapper.selectById(id) == null) return id;
        }
        throw new IllegalStateException("template id collision");
    }

    private static Integer inferRoot(String scope) {
        for (Map.Entry<String, Integer> e : ROOT_CATEGORIES.entrySet()) {
            if (scope.contains(e.getKey())) return e.getValue();
        }
        return null;
    }

    /* ---------------- 领券 ---------------- */

    public UserCoupon receive(long userId, String templateId) {
        CouponTemplate t = templateMapper.selectById(templateId);
        if (t == null) throw BizException.of(4001, "优惠券不存在");
        if (Instant.now().isAfter(t.getEndAt())) throw BizException.of(4002, "活动已结束");
        int r = cache.tryReceive(t, userId); // Lua：剩余量 > 0 且 已领 < 限领 → DECR + HINCRBY
        if (r == 1) throw BizException.of(4003, "手慢了，优惠券已抢光");
        if (r == 2) throw BizException.of(4004, "已达领取上限");
        try {
            return writer.persistReceive(t, userId);
        } catch (RuntimeException e) {
            cache.rollback(templateId, userId); // 落库失败回补 Redis
            throw e;
        }
    }

    /* ---------------- 用户券 ---------------- */

    /** 先把过期的 UNUSED 置为 EXPIRED，再按领取时间倒序返回 */
    public List<UserCoupon> userCoupons(long userId, String status) {
        userCouponMapper.expire(userId, Instant.now());
        return userCouponMapper.selectList(Wrappers.<UserCoupon>lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(status != null && !status.isEmpty(), UserCoupon::getStatus, status)
                .orderByDesc(UserCoupon::getCreatedAt).orderByDesc(UserCoupon::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean lock(CouponOpReq req) {
        if (req == null || req.couponId() == null || req.userId() == null) throw BizException.of(400, "请求参数错误");
        if (userCouponMapper.lock(req.couponId(), req.userId(), req.orderId(), Instant.now()) == 0) {
            throw BizException.of(4005, "优惠券不可用");
        }
        return true;
    }

    /** LOCKED → UNUSED（幂等：已释放直接返回 true） */
    @Transactional(rollbackFor = Exception.class)
    public boolean release(CouponOpReq req) {
        if (req == null || req.couponId() == null) throw BizException.of(400, "请求参数错误");
        userCouponMapper.release(req.couponId(), req.userId(), req.orderId());
        return true;
    }

    /** LOCKED → USED（幂等：已核销返回 true；其它状态返回 false） */
    @Transactional(rollbackFor = Exception.class)
    public boolean use(CouponOpReq req) {
        if (req == null || req.couponId() == null) throw BizException.of(400, "请求参数错误");
        if (userCouponMapper.use(req.couponId(), req.userId(), req.orderId()) > 0) return true;
        UserCoupon c = userCouponMapper.selectById(req.couponId());
        return c != null && "USED".equals(c.getStatus());
    }

    public UserCoupon grant(GrantCouponReq req) {
        if (req == null || req.userId() == null || req.templateId() == null) throw BizException.of(400, "请求参数错误");
        CouponTemplate t = templateMapper.selectById(req.templateId());
        if (t == null) throw BizException.of(4001, "优惠券不存在");
        return writer.grant(t, req.userId(), req.amount(), req.title());
    }
}
