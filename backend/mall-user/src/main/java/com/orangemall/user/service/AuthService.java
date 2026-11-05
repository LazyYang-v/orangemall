package com.orangemall.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.orangemall.api.client.MarketingInnerClient;
import com.orangemall.api.dto.GrantCouponReq;
import com.orangemall.common.BizException;
import com.orangemall.common.Const;
import com.orangemall.common.IdGenerator;
import com.orangemall.common.JwtUtil;
import com.orangemall.user.dto.Dtos.LoginReq;
import com.orangemall.user.dto.Dtos.LoginResp;
import com.orangemall.user.dto.Dtos.RegisterReq;
import com.orangemall.user.dto.Dtos.SmsResp;
import com.orangemall.user.dto.Dtos.UserProfile;
import com.orangemall.user.entity.User;
import com.orangemall.user.mapper.UserMapper;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {
    /** 演示环境固定验证码（与 Mock 一致） */
    public static final String DEMO_SMS_CODE = "123456";
    public static final String NEW_USER_COUPON = "CT04";
    public static final int NEW_USER_POINTS = 200;
    private static final Pattern PHONE = Pattern.compile("^1\\d{10}$");
    private static final String K_SMS_LIMIT = "auth:sms:limit:";
    private static final String K_SMS_CODE = "auth:sms:code:";
    private static final int SMS_INTERVAL = 60;

    private final UserMapper userMapper;
    private final PointsService pointsService;
    private final MarketingInnerClient marketing;
    private final StringRedisTemplate redis;

    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.ttl-seconds:604800}")
    private long ttlSeconds;

    public LoginResp login(LoginReq req) {
        User u = req.phone() == null ? null : findByPhone(req.phone());
        if (u == null) throw BizException.of(1001, "账号不存在，请先注册");
        boolean sms = req.smsCode() != null && !req.smsCode().isEmpty();
        boolean ok = sms ? DEMO_SMS_CODE.equals(req.smsCode()) : Passwords.matches(u.getSalt(), req.password(), u.getPasswordHash());
        if (!ok) throw BizException.of(1002, sms ? "验证码错误" : "账号或密码错误");
        if (sms) redis.delete(K_SMS_CODE + u.getPhone());
        return issue(u);
    }

    /**
     * 注册：写用户（初始 200 积分）+ 积分流水，再调营销服务发新人券 CT04。
     * 用户服务作为 TM 开启 Seata 全局事务，发券失败则注册整体回滚。
     */
    @GlobalTransactional(name = "user-register", rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public User register(RegisterReq req) {
        String phone = req.phone();
        if (phone == null || !PHONE.matcher(phone).matches()) throw BizException.of(1003, "手机号格式不正确");
        if (findByPhone(phone) != null) throw BizException.of(1004, "该手机号已注册");
        if (req.password() == null || req.password().isEmpty()) throw BizException.of(400, "请输入密码");
        User u = new User();
        u.setId(IdGenerator.nextId());
        u.setPhone(phone);
        u.setSalt(Passwords.newSalt());
        u.setPasswordHash(Passwords.hash(u.getSalt(), req.password()));
        u.setNickname(req.nickname() == null || req.nickname().isBlank() ? "橙友" + phone.substring(phone.length() - 4) : req.nickname().trim());
        u.setLevel(1);
        u.setPoints(NEW_USER_POINTS);
        u.setCreditScore(600);
        u.setBlocked(false);
        u.setRiskHits(0);
        u.setLastAction("正常");
        u.setRole("USER");
        try {
            userMapper.insert(u);
        } catch (DuplicateKeyException e) {
            throw BizException.of(1004, "该手机号已注册");
        }
        pointsService.addRecord(u.getId(), NEW_USER_POINTS, "新用户注册奖励");
        marketing.grant(new GrantCouponReq(u.getId(), NEW_USER_COUPON, null, null)).unwrap();
        return u;
    }

    public LoginResp issue(User u) {
        return new LoginResp(JwtUtil.create(u.getId(), u.getRole(), ttlSeconds, secret), UserProfile.of(u));
    }

    /** 发送验证码：同手机号 60s 限频（SET NX EX） */
    public SmsResp sms(String phone) {
        if (phone == null || !PHONE.matcher(phone).matches()) throw BizException.of(1003, "手机号格式不正确");
        Boolean first = redis.opsForValue().setIfAbsent(K_SMS_LIMIT + phone, "1", Duration.ofSeconds(SMS_INTERVAL));
        if (!Boolean.TRUE.equals(first)) {
            Long left = redis.getExpire(K_SMS_LIMIT + phone);
            throw BizException.of(4290, "验证码发送过于频繁，请 " + (left == null || left <= 0 ? SMS_INTERVAL : left) + " 秒后再试");
        }
        // 演示环境不接短信网关：验证码固定 123456，存 5 分钟
        redis.opsForValue().set(K_SMS_CODE + phone, DEMO_SMS_CODE, Duration.ofMinutes(5));
        return new SmsResp(true, "演示环境验证码固定为 " + DEMO_SMS_CODE);
    }

    /** 登出：token 有效则 jti 进黑名单，TTL = token 剩余有效期（网关校验时拦截） */
    public void logout(String authorization) {
        if (authorization == null) return;
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : authorization.trim();
        if (token.isEmpty()) return;
        JwtUtil.Claims c = JwtUtil.parse(token, secret);
        if (c == null || c.jti() == null) return;
        redis.opsForValue().set(Const.K_TOKEN_BLACKLIST + c.jti(), String.valueOf(c.userId()), Duration.ofSeconds(c.ttlSeconds()));
    }

    public User findByPhone(String phone) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getPhone, phone));
    }

    public User requireUser(long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw BizException.of(401, "登录已过期，请重新登录");
        return u;
    }
}
