package com.orangemall.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.orangemall.common.BizException;
import com.orangemall.common.Const;
import com.orangemall.user.dto.Dtos.RiskUserVO;
import com.orangemall.user.entity.User;
import com.orangemall.user.mapper.UserMapper;
import com.orangemall.web.OpsLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.DefaultTypedTuple;
import io.seata.spring.annotation.GlobalLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 风控数据：DB 为准，Redis 只读副本供秒杀 / 订单服务快速判断。
 * <ul>
 *   <li>ZSET {@link Const#K_RISK_CREDIT}：member=userId，score=信用分（全部用户）</li>
 *   <li>SET {@link Const#K_RISK_BLOCKED}：member=userId，后台手动拉黑（user.blocked=1）的用户</li>
 * </ul>
 * 拦截规则（与 Mock 一致）：信用分 &lt; 300 或在黑名单 SET 中。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskService {
    private static final String TMP_SUFFIX = ":rebuilding";
    private final UserMapper userMapper;
    private final StringRedisTemplate redis;
    private final OpsLogger ops;

    /** 启动时全量重建：先写临时 key 再 RENAME，避免重建窗口内读到空集合 */
    @EventListener(ApplicationReadyEvent.class)
    public void rebuild() {
        try {
            List<User> users = userMapper.selectList(Wrappers.<User>lambdaQuery()
                    .select(User::getId, User::getCreditScore, User::getBlocked));
            String zTmp = Const.K_RISK_CREDIT + TMP_SUFFIX, sTmp = Const.K_RISK_BLOCKED + TMP_SUFFIX;
            redis.delete(List.of(zTmp, sTmp));
            Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
            List<String> blocked = users.stream().filter(u -> Boolean.TRUE.equals(u.getBlocked()))
                    .map(u -> String.valueOf(u.getId())).toList();
            users.forEach(u -> tuples.add(new DefaultTypedTuple<>(String.valueOf(u.getId()), (double) u.getCreditScore())));
            if (!tuples.isEmpty()) {
                redis.opsForZSet().add(zTmp, tuples);
                redis.rename(zTmp, Const.K_RISK_CREDIT);
            } else redis.delete(Const.K_RISK_CREDIT);
            if (!blocked.isEmpty()) {
                redis.opsForSet().add(sTmp, blocked.toArray(String[]::new));
                redis.rename(sTmp, Const.K_RISK_BLOCKED);
            } else redis.delete(Const.K_RISK_BLOCKED);
            log.info("risk cache rebuilt: {} users, {} blocked", users.size(), blocked.size());
        } catch (Exception e) {
            log.error("risk cache rebuild failed", e);
        }
    }

    /** 新用户 / 信用分变化时同步 ZSET */
    public void syncCredit(long userId, int creditScore) {
        try {
            redis.opsForZSet().add(Const.K_RISK_CREDIT, String.valueOf(userId), creditScore);
        } catch (Exception e) {
            log.warn("sync credit failed: {}", e.getMessage());
        }
    }

    public List<RiskUserVO> list() {
        return userMapper.selectList(Wrappers.<User>lambdaQuery().eq(User::getRole, "USER")).stream()
                .sorted(Comparator.comparingInt(User::getCreditScore).thenComparing(User::getId))
                .map(RiskUserVO::of).toList();
    }

    /** @GlobalLock：本地写与 Seata 全局事务（积分变动）改同一行时，等待全局锁释放，避免 AT 回滚时脏写 */
    @GlobalLock
    @Transactional(rollbackFor = Exception.class)
    public void setBlocked(long userId, boolean blocked) {
        User u = userMapper.selectById(userId);
        if (u == null) throw BizException.of(9006, "用户不存在");
        User up = new User();
        up.setId(userId);
        up.setBlocked(blocked);
        userMapper.updateById(up);
        // DB 提交后再同步 Redis（Redis 失败不影响 DB，下次启动全量重建可自愈）
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (blocked) redis.opsForSet().add(Const.K_RISK_BLOCKED, String.valueOf(userId));
                else redis.opsForSet().remove(Const.K_RISK_BLOCKED, String.valueOf(userId));
                syncCredit(userId, u.getCreditScore());
                ops.log("RISK", "[风控] 用户 " + u.getNickname() + " " + (blocked ? "加入" : "移出") + "黑名单");
            }
        });
    }
}
