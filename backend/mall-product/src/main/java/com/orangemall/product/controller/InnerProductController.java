package com.orangemall.product.controller;

import com.orangemall.api.dto.SkuInfo;
import com.orangemall.api.dto.StockOpReq;
import com.orangemall.common.Result;
import com.orangemall.product.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 实现 com.orangemall.api.client.ProductInnerClient（网关不暴露 /inner/**） */
@RestController
@RequestMapping("/inner")
@RequiredArgsConstructor
public class InnerProductController {
    private final StockService stockService;

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
}
