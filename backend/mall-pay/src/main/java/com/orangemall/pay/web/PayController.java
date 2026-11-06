package com.orangemall.pay.web;

import com.orangemall.common.Result;
import com.orangemall.pay.service.PayService;
import com.orangemall.web.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 契约 §6。/payments/notify 无鉴权（由支付平台服务器回调，用验签保护） */
@RestController
public class PayController {
    private final PayService pay;

    public PayController(PayService pay) {
        this.pay = pay;
    }

    @PostMapping("/payments")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return Result.ok(pay.create(UserContext.requireUser(),
                Long.parseLong(String.valueOf(body.get("orderId"))), (String) body.get("channel")));
    }

    @GetMapping("/payments/{paymentNo}")
    public Result<Map<String, Object>> status(@PathVariable String paymentNo) {
        return Result.ok(pay.status(UserContext.requireUser(), paymentNo));
    }

    @PostMapping("/payments/{paymentNo}/sandbox-pay")
    public Result<Map<String, Object>> sandboxPay(@PathVariable String paymentNo, @RequestBody Map<String, Object> body) {
        return Result.ok(pay.sandboxPay(UserContext.requireUser(), paymentNo, Boolean.TRUE.equals(body.get("success"))));
    }

    @PostMapping("/payments/notify")
    public Result<Map<String, Object>> notify(@RequestBody Map<String, Object> body) {
        Long refundId = body.get("refundId") == null ? null : Long.parseLong(String.valueOf(body.get("refundId")));
        return Result.ok(pay.notify(String.valueOf(body.get("paymentNo")), refundId,
                ((Number) body.get("amount")).intValue(),
                String.valueOf(body.get("tradeStatus")), String.valueOf(body.get("sign"))));
    }
}
