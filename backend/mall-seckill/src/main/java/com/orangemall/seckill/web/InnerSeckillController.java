package com.orangemall.seckill.web;

import com.orangemall.common.Result;
import com.orangemall.seckill.service.SeckillService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** inner：首页秒杀预览（商品服务聚合 home 数据时调用） */
@RestController
@RequestMapping("/inner")
public class InnerSeckillController {
    private final SeckillService seckill;

    public InnerSeckillController(SeckillService seckill) {
        this.seckill = seckill;
    }

    @GetMapping("/seckill/preview")
    public Result<Map<String, Object>> preview() {
        return Result.ok(seckill.preview());
    }
}
