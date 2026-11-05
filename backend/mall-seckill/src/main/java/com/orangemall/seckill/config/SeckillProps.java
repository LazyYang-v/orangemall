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
}
