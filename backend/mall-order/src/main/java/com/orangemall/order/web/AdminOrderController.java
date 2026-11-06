package com.orangemall.order.web;

import com.orangemall.common.PageResult;
import com.orangemall.common.Result;
import com.orangemall.order.domain.OrderVO;
import com.orangemall.common.BizException;
import com.orangemall.order.service.OrderService;
import com.orangemall.order.service.StockReconcileService;
import com.orangemall.web.UserContext;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** 运营后台订单部分：契约 §9（网关已保证 ADMIN，这里 requireAdmin 兜底） */
@RestController
@RequestMapping("/admin")
public class AdminOrderController {
    private final OrderService orders;
    private final StockReconcileService reconciler;

    public AdminOrderController(OrderService orders, StockReconcileService reconciler) {
        this.orders = orders;
        this.reconciler = reconciler;
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

    /**
     * 按需库存对账（不动定时任务的检查点）：窗口 = [now − hours, now − settleSec)。
     * settleSec 默认与定时任务相同（300s）；更小的值会把正常在途的单当成异常，只用于演练。
     */
    @PostMapping("/orders/stock-reconcile")
    public Result<StockReconcileService.Report> stockReconcile(@RequestParam(defaultValue = "24") int hours,
                                                               @RequestParam(defaultValue = "300") int settleSec) {
        UserContext.requireAdmin();
        if (hours < 1 || hours > 24 * 7 || settleSec < 0) throw BizException.of(400, "请求参数错误");
        Instant now = Instant.now();
        return Result.ok(reconciler.reconcile(now.minus(Duration.ofHours(hours)), now.minusSeconds(settleSec)));
    }
}
