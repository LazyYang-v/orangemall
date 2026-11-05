package com.orangemall.seckill.web;

import com.orangemall.common.Result;
import com.orangemall.seckill.service.AdminSeckillService;
import com.orangemall.web.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 契约 §9 秒杀后台：会话/指标、预热、并发模拟、缓存一致性 */
@RestController
public class AdminSeckillController {
    private final AdminSeckillService admin;

    public AdminSeckillController(AdminSeckillService admin) {
        this.admin = admin;
    }

    @GetMapping("/admin/seckill")
    public Result<Map<String, Object>> view() {
        UserContext.requireAdmin();
        return Result.ok(admin.view());
    }

    @PostMapping("/admin/seckill/{activityId}/warmup")
    public Result<Boolean> warmup(@PathVariable String activityId) {
        UserContext.requireAdmin();
        return Result.ok(admin.warmup(activityId));
    }

    /** 仅压测/演示：N 个模拟用户真实并发走 Lua + Kafka 全链路 */
    @PostMapping("/admin/seckill/{activityId}/simulate")
    public Result<Map<String, Object>> simulate(@PathVariable String activityId, @RequestBody Map<String, Object> body) {
        UserContext.requireAdmin();
        return Result.ok(admin.simulate(activityId, ((Number) body.getOrDefault("users", 500)).intValue()));
    }

    @GetMapping("/admin/cache/check")
    public Result<Map<String, Object>> cacheCheck() {
        UserContext.requireAdmin();
        return Result.ok(admin.cacheCheck());
    }

    @PostMapping("/admin/cache/repair")
    public Result<Boolean> cacheRepair(@RequestBody Map<String, Object> body) {
        UserContext.requireAdmin();
        return Result.ok(admin.repair(String.valueOf(body.get("key"))));
    }
}
