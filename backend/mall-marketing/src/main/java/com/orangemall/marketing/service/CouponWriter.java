package com.orangemall.marketing.service;

import com.orangemall.common.BizException;
import com.orangemall.common.IdGenerator;
import com.orangemall.marketing.entity.CouponTemplate;
import com.orangemall.marketing.entity.UserCoupon;
import com.orangemall.marketing.mapper.CouponTemplateMapper;
import com.orangemall.marketing.mapper.UserCouponMapper;
import io.seata.spring.annotation.GlobalLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;

/** 用户券落库（独立 bean，保证事务 / 全局锁代理生效） */
@Component
@RequiredArgsConstructor
public class CouponWriter {
    private final CouponTemplateMapper templateMapper;
    private final UserCouponMapper userCouponMapper;
    private final CouponStockCache cache;

    /**
     * 用户领券落库：Redis Lua 已预扣，DB 再以 received &lt; total 兜底防超发。
     * {@code @GlobalLock}：coupon_template 行也会被 Seata 全局事务（注册发券）修改，本地写需等待全局锁，避免 AT 回滚脏写。
     */
    @GlobalLock
    @Transactional(rollbackFor = Exception.class)
    public UserCoupon persistReceive(CouponTemplate t, long userId) {
        if (templateMapper.incReceived(t.getId()) == 0) throw BizException.of(4003, "手慢了，优惠券已抢光");
        return insert(t, userId, null, null);
    }

    /** 系统发券（Seata AT 分支）：DB 提交后同步 Redis 计数 */
    @Transactional(rollbackFor = Exception.class)
    public UserCoupon grant(CouponTemplate t, long userId, Integer amount, String title) {
        templateMapper.incReceivedForce(t.getId());
        UserCoupon c = insert(t, userId, amount, title);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cache.onGranted(t.getId(), userId);
            }
        });
        return c;
    }

    private UserCoupon insert(CouponTemplate t, long userId, Integer amount, String title) {
        Instant now = Instant.now();
        UserCoupon c = new UserCoupon();
        c.setId(IdGenerator.nextId());
        c.setUserId(userId);
        c.setTemplateId(t.getId());
        c.setTitle(title != null && !title.isBlank() ? title : t.getTitle());
        c.setType(t.getType());
        c.setThreshold(t.getThreshold());
        c.setAmount(amount != null ? amount : t.getAmount());
        c.setScope(t.getScope());
        c.setScopeRootId(t.getScopeRootId());
        c.setStatus("UNUSED");
        c.setValidFrom(now);
        c.setValidTo(now.plus(Duration.ofDays(t.getValidDays())));
        userCouponMapper.insert(c);
        return c;
    }
}
