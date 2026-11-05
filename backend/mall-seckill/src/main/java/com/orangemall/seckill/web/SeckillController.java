package com.orangemall.seckill.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.orangemall.common.Result;
import com.orangemall.seckill.service.SeckillService;
import com.orangemall.web.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 契约 §7。网关已去掉 /api 前缀 */
@RestController
public class SeckillController {
    private final SeckillService seckill;

    public SeckillController(SeckillService seckill) {
        this.seckill = seckill;
    }

    @GetMapping("/seckill/sessions")
    public Result<List<SeckillService.SessionVO>> sessions() {
        return Result.ok(seckill.sessions());
    }

    @GetMapping("/seckill/items/{activityId}")
    public Result<Map<String, Object>> item(@PathVariable String activityId) {
        SeckillService.SeckillItemVO it = seckill.item(activityId);
        Long uid = UserContext.userId();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("activityId", it.activityId());
        out.put("sessionId", it.sessionId());
        out.put("skuId", it.skuId());
        out.put("spuId", it.spuId());
        out.put("name", it.name());
        out.put("art", it.art());
        out.put("tint", it.tint());
        out.put("seckillPrice", it.seckillPrice());
        out.put("originalPrice", it.originalPrice());
        out.put("totalStock", it.totalStock());
        out.put("remainStock", it.remainStock());
        out.put("limitPerUser", it.limitPerUser());
        out.put("startAt", it.startAt());
        out.put("endAt", it.endAt());
        out.put("status", it.status());
        out.put("bought", uid != null && seckill.bought(activityId, uid));
        out.put("serverTime", System.currentTimeMillis());
        return Result.ok(out);
    }

    @GetMapping("/seckill/{activityId}/path")
    public Result<Map<String, Object>> path(@PathVariable String activityId) {
        return Result.ok(Map.of("path", seckill.issuePath(activityId)));
    }

    @PostMapping("/seckill/{activityId}/{path}/orders")
    public Result<Map<String, Object>> submit(@PathVariable String activityId, @PathVariable String path,
                                              @RequestBody(required = false) Map<String, Object> body) {
        Long addressId = body == null || body.get("addressId") == null || String.valueOf(body.get("addressId")).isEmpty()
                ? null : Long.parseLong(String.valueOf(body.get("addressId")));
        return Result.ok(seckill.submit(activityId, path, addressId));
    }

    @GetMapping("/seckill/{activityId}/result")
    public Result<JsonNode> result(@PathVariable String activityId) {
        return Result.ok(seckill.result(activityId, UserContext.requireUser()));
    }
}
