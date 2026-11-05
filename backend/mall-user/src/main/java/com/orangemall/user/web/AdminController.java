package com.orangemall.user.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangemall.common.BizException;
import com.orangemall.common.Const;
import com.orangemall.common.Result;
import com.orangemall.user.dto.Dtos.BlockReq;
import com.orangemall.user.dto.Dtos.RiskUserVO;
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
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    private static final int MAX_LOGS = 100;
    private final RiskService risk;
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

    /** 仅 Mock 环境有意义：真实后端不提供一键重置，返回 true 保持前端兼容 */
    @PostMapping("/mock/reset")
    public Result<Boolean> reset() {
        UserContext.requireAdmin();
        return Result.ok();
    }
}
