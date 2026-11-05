package com.orangemall.order.web;

import com.orangemall.api.dto.OrderBrief;
import com.orangemall.api.dto.SeckillOrderReq;
import com.orangemall.common.Result;
import com.orangemall.order.service.OrderService;
import org.springframework.web.bind.annotation.*;

/** 服务间 inner 接口（只在内网被 Feign 调用） */
@RestController
@RequestMapping("/inner")
public class InnerOrderController {
    private final OrderService orders;

    public InnerOrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/orders/{id}")
    public Result<OrderBrief> brief(@PathVariable long id) {
        return Result.ok(orders.brief(id));
    }

    @PostMapping("/orders/seckill")
    public Result<Long> createSeckill(@RequestBody SeckillOrderReq req) {
        return Result.ok(orders.createSeckill(req));
    }
}
