package com.orangemall.api.client;

import com.orangemall.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@FeignClient(name = "mall-seckill", contextId = "seckillInner", path = "/inner")
public interface SeckillInnerClient {
    /** 首页秒杀预览：{ endAt, items(前 4 个，SeckillItem 结构) }，取进行中场次 */
    @GetMapping("/seckill/preview")
    Result<Map<String, Object>> preview();
}
