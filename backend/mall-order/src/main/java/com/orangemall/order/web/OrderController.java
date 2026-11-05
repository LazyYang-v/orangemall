package com.orangemall.order.web;

import com.orangemall.common.PageResult;
import com.orangemall.common.Result;
import com.orangemall.order.domain.OrderVO;
import com.orangemall.order.service.OrderService;
import com.orangemall.web.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 用户订单：契约 §5。网关已去掉 /api 前缀 */
@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping("/preview")
    public Result<Map<String, Object>> preview(@RequestBody Map<String, Object> body) {
        return Result.ok(orders.preview(UserContext.requireUser(), body));
    }

    @PostMapping
    public Result<Map<String, Object>> submit(@RequestBody Map<String, Object> body) {
        return Result.ok(orders.submit(UserContext.requireUser(), body));
    }

    @GetMapping
    public Result<PageResult<OrderVO>> list(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(orders.list(UserContext.requireUser(), status, keyword, page, size));
    }

    @GetMapping("/counts")
    public Result<Map<String, Integer>> counts() {
        return Result.ok(orders.counts(UserContext.requireUser()));
    }

    @GetMapping("/{id}")
    public Result<OrderVO> detail(@PathVariable long id) {
        return Result.ok(orders.get(UserContext.requireUser(), id, UserContext.isAdmin()));
    }

    @PostMapping("/{id}/cancel")
    public Result<Boolean> cancel(@PathVariable long id) {
        return Result.ok(orders.cancel(UserContext.requireUser(), id));
    }

    @PostMapping("/{id}/confirm")
    public Result<Map<String, Object>> confirm(@PathVariable long id) {
        return Result.ok(orders.confirm(UserContext.requireUser(), id));
    }
}
