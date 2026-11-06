package com.orangemall.pay.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orangemall.api.dto.PaidPayment;
import com.orangemall.common.Result;
import com.orangemall.pay.entity.Payment;
import com.orangemall.pay.mapper.PayMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * 服务间内部接口（/inner/** 由 InnerKeyFilter 保护）：
 * 支付↔订单对账数据源 —— 返回「已支付成功」的流水，供订单服务核对 PAID 状态兜底（OM-003）。
 */
@RestController
@RequestMapping("/inner/pay")
@RequiredArgsConstructor
public class InnerPayController {
    private final PayMapper payMapper;

    /** paidAt 依次递增的支付流水（每页至多 500 条，对账 job 每 5 分钟调用一次足够） */
    @GetMapping("/paid")
    public Result<List<PaidPayment>> paidSince(@RequestParam("from") long fromEpochSec) {
        List<Payment> rows = payMapper.selectList(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getStatus, "SUCCESS")
                .gt(Payment::getPaidAt, Instant.ofEpochSecond(fromEpochSec))
                .orderByAsc(Payment::getPaidAt)
                .last("LIMIT 500"));
        List<PaidPayment> out = rows.stream()
                .map(p -> new PaidPayment(p.getOrderId(), p.getPaymentNo(), p.getChannel(), p.getPaidAt()))
                .toList();
        return Result.ok(out);
    }
}
