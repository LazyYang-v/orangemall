package com.orangemall.pay.web;

import com.orangemall.common.PageResult;
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

import java.util.Map;

/** 运营售后管理：网关已保证 ADMIN，这里 requireAdmin 兜底 */
@RestController
@RequestMapping("/admin/refunds")
public class AdminRefundController {
    private final RefundService refunds;

    public AdminRefundController(RefundService refunds) {
        this.refunds = refunds;
    }

    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(@RequestParam(required = false) String status,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        UserContext.requireAdmin();
        return Result.ok(refunds.adminList(status, page, size));
    }

    @PostMapping("/{id}/approve")
    public Result<Map<String, Object>> approve(@PathVariable long id, @RequestBody Map<String, Object> body) {
        long adminId = UserContext.requireAdmin();
        boolean pass = Boolean.TRUE.equals(body.get("pass"));
        return Result.ok(refunds.approve(adminId, id, pass, body.get("remark") == null ? "" : String.valueOf(body.get("remark"))));
    }
}
