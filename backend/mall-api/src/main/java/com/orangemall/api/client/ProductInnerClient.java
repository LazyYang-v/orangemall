package com.orangemall.api.client;

import com.orangemall.api.dto.SkuInfo;
import com.orangemall.api.dto.StockItem;
import com.orangemall.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "mall-product", contextId = "productInner", path = "/inner")
public interface ProductInnerClient {
    /** 批量查询 SKU 快照（不存在的 skuId 直接不返回） */
    @PostMapping("/skus/batch")
    Result<List<SkuInfo>> skus(@RequestBody List<Long> skuIds);

    /** 扣减库存：UPDATE sku SET stock=stock-n, sales=sales+n WHERE id=? AND stock>=n；任一失败抛 3001（Seata AT 分支） */
    @PostMapping("/stock/deduct")
    Result<Boolean> deduct(@RequestBody List<StockItem> items);

    /** 回补库存（关单），sales 同步回退 */
    @PostMapping("/stock/restore")
    Result<Boolean> restore(@RequestBody List<StockItem> items);
}
