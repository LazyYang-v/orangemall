package com.orangemall.seckill.web;

import com.orangemall.common.Result;
import com.orangemall.seckill.entity.SeckillDeadLetter;
import com.orangemall.seckill.service.AdminSeckillService;
import com.orangemall.seckill.service.SeckillReliabilityService;
import com.orangemall.web.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 契约 §9 秒杀后台：会话/指标、预热、并发模拟、缓存一致性 */
@RestController
public class AdminSeckillController {
    private final AdminSeckillService admin;
    private final SeckillReliabilityService reliability;

    public AdminSeckillController(AdminSeckillService admin, SeckillReliabilityService reliability) {
        this.admin = admin;
        this.reliability = reliability;
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

    /* ---------- OM-013 / OM-019 可靠性 ---------- */

    @GetMapping("/admin/seckill/health")
    public Result<Map<String, Object>> health() {
        UserContext.requireAdmin();
        return Result.ok(reliability.health());
    }

    @GetMapping("/admin/seckill/dead-letters")
    public Result<List<SeckillDeadLetter>> deadLetters(@RequestParam(required = false) String status) {
        UserContext.requireAdmin();
        return Result.ok(reliability.deadLetters(status));
    }

    @PostMapping("/admin/seckill/dead-letters/{id}/resolve")
    public Result<Boolean> resolve(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body) {
        long adminId = UserContext.requireAdmin();
        Object remark = body == null ? null : body.get("remark");
        return Result.ok(reliability.resolve(id, remark == null ? null : String.valueOf(remark), adminId));
    }

    @PostMapping("/admin/seckill/{activityId}/inflight/reset")
    public Result<Map<String, Object>> inflightReset(@PathVariable String activityId,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        long adminId = UserContext.requireAdmin();
        boolean force = body != null && Boolean.TRUE.equals(body.get("force"));
        return Result.ok(reliability.manualReset(activityId, force, adminId));
    }

    /** 故障演练：暂停 / 恢复秒杀主消费者（模拟消费者宕机） */
    @PostMapping("/admin/seckill/consumer/{action:pause|resume}")
    public Result<Map<String, Object>> consumer(@PathVariable String action) {
        long adminId = UserContext.requireAdmin();
        return Result.ok(reliability.consumer("pause".equals(action), adminId));
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
