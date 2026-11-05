package com.orangemall.product.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class ProductConfig {

    @Bean
    public RedisMessageListenerContainer redisListenerContainer(RedisConnectionFactory cf) {
        RedisMessageListenerContainer c = new RedisMessageListenerContainer();
        c.setConnectionFactory(cf);
        return c;
    }

    /** 延迟双删的第二次 DEL */
    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService cacheDeleteScheduler() {
        return Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "cache-double-delete");
            t.setDaemon(true);
            return t;
        });
    }

    /** 首页聚合并行调用秒杀 / 营销服务 */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService homeExecutor() {
        return Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "home-agg");
            t.setDaemon(true);
            return t;
        });
    }
}
