package com.orangemall.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("seckill_activity")
public class SeckillActivity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String sessionId;
    private Long skuId;
    private Long spuId;
    private String name;
    private String art;
    private String tint;
    private Integer seckillPrice;
    private Integer originalPrice;
    private Integer totalStock;
    private Integer stock;
    private Integer limitPerUser;
    private Integer warmed;
    private Instant startAt;
    private Instant endAt;
}
