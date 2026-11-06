package com.orangemall.user.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangemall.common.BizException;
import com.orangemall.common.Const;
import com.orangemall.common.PageResult;
import com.orangemall.common.Result;
import com.orangemall.user.dto.Dtos.BlockReq;
import com.orangemall.user.dto.Dtos.RiskUserVO;
import com.orangemall.user.entity.User;
import com.orangemall.user.mapper.UserMapper;
import com.orangemall.user.service.RiskService;
import com.orangemall.web.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    private static final int MAX_LOGS = 100;
    private final RiskService risk;
    private final UserMapper userMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    @GetMapping("/risk")
    public Result<List<RiskUserVO>> risk() {
        UserContext.requireAdmin();
        return Result.ok(risk.list());
    }

    @PutMapping("/risk/{userId}")
    public Result<Boolean> block(@PathVariable("userId") long userId, @RequestBody(required = false) BlockReq req) {
        UserContext.requireAdmin();
        if (req == null || req.blocked() == null) throw BizException.of(400, "请求参数错误");
        risk.setBlocked(userId, req.blocked());
        return Result.ok();
    }

    /** 链路日志：各服务 OpsLogger LPUSH 到 Redis List ops:logs（新的在前），最多返回 100 条 */
    @GetMapping("/logs")
    public Result<List<Map<String, Object>>> logs(@RequestParam(value = "type", required = false) String type) {
        UserContext.requireAdmin();
        List<String> raw = redis.opsForList().range(Const.K_OPS_LOGS, 0, 199);
        List<Map<String, Object>> out = new ArrayList<>();
        if (raw != null) {
            for (String s : raw) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = mapper.readValue(s, Map.class);
                    if (type == null || type.isEmpty() || type.equals(m.get("type"))) out.add(m);
                } catch (Exception e) {
                    log.debug("skip malformed ops log: {}", s);
                }
                if (out.size() >= MAX_LOGS) break;
            }
        }
        return Result.ok(out);
    }

    /* ==================== 用户管理（Phase 2） ==================== */

    /** 用户列表：关键字匹配 phone/nickname，按注册时间倒序 */
    @GetMapping("/users")
    public Result<PageResult<Map<String, Object>>> users(@RequestParam(required = false) String keyword,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        UserContext.requireAdmin();
        LambdaQueryWrapper<User> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            w.and(x -> x.like(User::getPhone, keyword).or().like(User::getNickname, keyword));
        }
        w.orderByDesc(User::getCreatedAt);
        int total = userMapper.selectCount(w).intValue();
        List<Map<String, Object>> records = userMapper.selectList(w.last("LIMIT " + (page - 1) * size + "," + size))
                .stream().map(AdminController::userVO).toList();
        return Result.ok(new PageResult<>(records, total, page, size));
    }

    /** 用户详情：档案 + 售后风险维度（发货风控值/最近动作） */
    @GetMapping("/users/{id}")
    public Result<Map<String, Object>> userDetail(@PathVariable("id") long id) {
        UserContext.requireAdmin();
        User u = userMapper.selectById(id);
        if (u == null) throw BizException.of(9006, "用户不存在");
        Map<String, Object> out = userVO(u);
        out.put("lastAction", u.getLastAction());
        out.put("blocked", Boolean.TRUE.equals(u.getBlocked()));
        return Result.ok(out);
    }

    private static Map<String, Object> userVO(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(u.getId()));
        m.put("phone", u.getPhone());
        m.put("nickname", u.getNickname());
        m.put("points", u.getPoints());
        m.put("creditScore", u.getCreditScore());
        m.put("riskHits", u.getRiskHits());
        m.put("level", u.getLevel());
        m.put("role", u.getRole());
        m.put("blocked", Boolean.TRUE.equals(u.getBlocked()));
        m.put("createdAt", u.getCreatedAt());
        return m;
    }

    /** 仅 Mock 环境有意义：真实后端不提供一键重置，返回 true 保持前端兼容 */
    @PostMapping("/mock/reset")
    public Result<Boolean> reset() {
        UserContext.requireAdmin();
        return Result.ok();
    }
}
