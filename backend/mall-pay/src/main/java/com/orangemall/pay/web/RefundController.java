package com.orangemall.pay.web;

import com.orangemall.common.Result;
import com.orangemall.pay.service.RefundService;
import com.orangemall.web.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 售后单（契约 §11 增量，docs/refund-design.md） */
@RestController
@RequestMapping("/refunds")
public class RefundController {
    private final RefundService refunds;

    public RefundController(RefundService refunds) {
        this.refunds = refunds;
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        long userId = UserContext.requireUser();
        return Result.ok(refunds.create(userId,
                Long.parseLong(String.valueOf(body.get("orderId"))),
                String.valueOf(body.get("type")),
                String.valueOf(body.get("reason"))));
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return Result.ok(refunds.list(UserContext.requireUser(), page, size));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable long id) {
        return Result.ok(refunds.detail(UserContext.requireUser(), id));
    }

    @PostMapping("/{id}/cancel")
    public Result<Boolean> cancel(@PathVariable long id) {
        return Result.ok(refunds.cancel(UserContext.requireUser(), id));
    }
}
