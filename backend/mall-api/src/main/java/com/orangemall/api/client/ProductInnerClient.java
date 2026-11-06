package com.orangemall.api.client;

import com.orangemall.api.dto.LedgerEntry;
import com.orangemall.api.dto.SkuInfo;
import com.orangemall.api.dto.StockOpReq;
import com.orangemall.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "mall-product", contextId = "productInner", path = "/inner")
public interface ProductInnerClient {
    /** 批量查询 SKU 快照（不存在的 skuId 直接不返回） */
    @PostMapping("/skus/batch")
    Result<List<SkuInfo>> skus(@RequestBody List<Long> skuIds);

    /**
     * 扣减库存并记 DEDUCT 流水（Seata AT 分支）：UPDATE sku SET stock=stock-n … WHERE stock>=n，任一失败抛 3001。
     * 同一订单重复调用幂等（已有流水即视为已扣）。
     */
    @PostMapping("/stock/deduct")
    Result<Boolean> deduct(@RequestBody StockOpReq req);

    /**
     * 回补库存（关单 / 退款），sales 同步回退。按订单幂等：每个 SKU 只回补一次，件数以该订单 DEDUCT 流水为准；
     * 返回实际回补的 SKU 行数（0 = 早已回补过 / 从未扣减）。
     */
    @PostMapping("/stock/restore")
    Result<Integer> restore(@RequestBody StockOpReq req);

    /** 库存对账：一批订单（≤ 500）的全部流水 */
    @PostMapping("/stock/ledger/by-orders")
    Result<List<LedgerEntry>> ledgerByOrders(@RequestBody List<Long> orderIds);

    /** 库存对账：[from, to)（epoch 毫秒）内有 DEDUCT 流水的订单号，按订单号升序最多 limit 个 */
    @GetMapping("/stock/ledger/deducted")
    Result<List<Long>> deducted(@RequestParam("from") long from, @RequestParam("to") long to, @RequestParam("limit") int limit);
}
