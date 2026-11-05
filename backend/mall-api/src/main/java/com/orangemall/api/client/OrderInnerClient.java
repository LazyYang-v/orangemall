package com.orangemall.api.client;

import com.orangemall.api.dto.OrderBrief;
import com.orangemall.api.dto.SeckillOrderReq;
import com.orangemall.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "mall-order", contextId = "orderInner", path = "/inner")
public interface OrderInnerClient {
    /** 不存在抛 5004 */
    @GetMapping("/orders/{id}")
    Result<OrderBrief> brief(@PathVariable("id") Long id);

    /** 创建秒杀订单（幂等：orderId 已存在直接返回），10 分钟延迟关单 */
    @PostMapping("/orders/seckill")
    Result<Long> createSeckill(@RequestBody SeckillOrderReq req);
}
