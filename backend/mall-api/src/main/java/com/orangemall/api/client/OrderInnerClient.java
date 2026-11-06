package com.orangemall.api.client;

import com.orangemall.api.dto.OrderBrief;
import com.orangemall.api.dto.RefundStateReq;
import com.orangemall.api.dto.SeckillOrderReq;
import com.orangemall.api.dto.StockItem;
import com.orangemall.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "mall-order", contextId = "orderInner", path = "/inner")
public interface OrderInnerClient {
    /** 不存在抛 5004 */
    @GetMapping("/orders/{id}")
    Result<OrderBrief> brief(@PathVariable("id") Long id);

    /** 创建秒杀订单（幂等：orderId 已存在直接返回），10 分钟延迟关单 */
    @PostMapping("/orders/seckill")
    Result<Long> createSeckill(@RequestBody SeckillOrderReq req);

    /** 订单商品行（skuId+count），用于退款回补库存 */
    @GetMapping("/orders/{id}/items")
    Result<List<StockItem>> items(@PathVariable("id") Long id);

    /** 售后状态流转（CAS：from 非空时需与当前状态一致；to 不合法抛 5008） */
    @PostMapping("/orders/{id}/refund-state")
    Result<Boolean> refundState(@PathVariable("id") Long id, @RequestBody RefundStateReq req);
}
