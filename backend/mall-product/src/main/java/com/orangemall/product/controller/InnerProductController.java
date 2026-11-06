package com.orangemall.product.controller;

import com.orangemall.api.dto.LedgerEntry;
import com.orangemall.api.dto.SkuInfo;
import com.orangemall.api.dto.StockOpReq;
import com.orangemall.common.Result;
import com.orangemall.product.mapper.StockLedgerMapper;
import com.orangemall.product.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/** 实现 com.orangemall.api.client.ProductInnerClient（网关不暴露 /inner/**） */
@RestController
@RequestMapping("/inner")
@RequiredArgsConstructor
public class InnerProductController {
    private final StockService stockService;
    private final StockLedgerMapper ledger;

    @PostMapping("/skus/batch")
    public Result<List<SkuInfo>> skus(@RequestBody List<Long> skuIds) {
        return Result.ok(stockService.skus(skuIds));
    }

    @PostMapping("/stock/deduct")
    public Result<Boolean> deduct(@RequestBody StockOpReq req) {
        return Result.ok(stockService.deduct(req));
    }

    @PostMapping("/stock/restore")
    public Result<Integer> restore(@RequestBody StockOpReq req) {
        return Result.ok(stockService.restore(req));
    }

    /** 库存对账：一批订单的流水（最多 500 单） */
    @PostMapping("/stock/ledger/by-orders")
    public Result<List<LedgerEntry>> ledgerByOrders(@RequestBody List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return Result.ok(List.of());
        if (orderIds.size() > 500) throw com.orangemall.common.BizException.of(400, "请求参数错误");
        return Result.ok(ledger.byOrders(orderIds));
    }

    /** 库存对账：[from, to)（epoch 毫秒）内扣过库存的订单号 */
    @GetMapping("/stock/ledger/deducted")
    public Result<List<Long>> deducted(@RequestParam long from, @RequestParam long to, @RequestParam(defaultValue = "10000") int limit) {
        return Result.ok(ledger.deductedBetween(Instant.ofEpochMilli(from), Instant.ofEpochMilli(to), Math.min(Math.max(limit, 1), 20000)));
    }
}
