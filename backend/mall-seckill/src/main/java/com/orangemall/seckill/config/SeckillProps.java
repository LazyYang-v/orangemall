package com.orangemall.seckill.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "seckill")
public class SeckillProps {
    private int bucketCapacity = 5;
    private double bucketRate = 2;
    private int minCredit = 300;
    private int pathTtlSeconds = 60;
    private int resultTtlSeconds = 86400;
    private String cacheStatsKey = "cache:stats";
    /** OM-013：在途数 >0 且持续不变超过该秒数 → 判定滞留并告警 */
    private int inflightStuckSeconds = 180;
    /** OM-013：滞留且无积压消息（判定为计数泄漏）时自动重置在途并回补 Redis 库存 */
    private boolean inflightAutoReset = true;
}
