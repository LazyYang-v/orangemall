package com.orangemall.order.web;

import com.orangemall.common.PageResult;
import com.orangemall.common.Result;
import com.orangemall.order.domain.OrderVO;
import com.orangemall.order.service.OrderService;
import com.orangemall.web.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 运营后台订单部分：契约 §9（网关已保证 ADMIN，这里 requireAdmin 兜底） */
@RestController
@RequestMapping("/admin")
public class AdminOrderController {
    private final OrderService orders;

    public AdminOrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        UserContext.requireAdmin();
        return Result.ok(orders.dashboard());
    }

    @GetMapping("/orders")
    public Result<PageResult<OrderVO>> list(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String source,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        UserContext.requireAdmin();
        return Result.ok(orders.adminList(status, source, keyword, page, size));
    }

    @PostMapping("/orders/{id}/ship")
    public Result<Boolean> ship(@PathVariable long id, @RequestBody Map<String, String> body) {
        UserContext.requireAdmin();
        orders.ship(id, body.get("company"), body.get("trackingNo"));
        return Result.ok();
    }

    /** 仅测试/演示：让延迟消息立即到期（把 payDeadline 置过去并直接走关单流程） */
    @PostMapping("/orders/{id}/expire")
    public Result<Boolean> expire(@PathVariable long id) {
        UserContext.requireAdmin();
        return Result.ok(orders.expireNow(id));
    }
}
