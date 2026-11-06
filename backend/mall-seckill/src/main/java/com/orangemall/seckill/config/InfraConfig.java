package com.orangemall.seckill.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class InfraConfig {

    /** Redisson 仅用于分布式锁（滚动建场/预热、一致性校验、后台预热/模拟），避免多实例并发执行 */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(@Value("${spring.data.redis.host:localhost}") String host,
                                         @Value("${spring.data.redis.port:6379}") int port) {
        Config c = new Config();
        c.setCodec(StringCodec.INSTANCE);
        c.setThreads(4);
        c.setNettyThreads(8);
        c.useSingleServer().setAddress("redis://" + host + ":" + port)
                .setConnectionPoolSize(8).setConnectionMinimumIdleSize(2)
                .setSubscriptionConnectionPoolSize(4).setSubscriptionConnectionMinimumIdleSize(1)
                .setTimeout(3000);
        return Redisson.create(c);
    }

    @Bean
    public DefaultRedisScript<Long> guardScript() {
        return script("lua/guard.lua", Long.class);
    }

    @Bean
    public DefaultRedisScript<Long> stockScript() {
        return script("lua/stock.lua", Long.class);
    }

    @Bean
    public DefaultRedisScript<Long> finishScript() {
        return script("lua/finish.lua", Long.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Bean
    public DefaultRedisScript<List> repairScript() {
        return script("lua/repair.lua", List.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Bean
    public DefaultRedisScript<List> inflightResetScript() {
        return script("lua/inflight_reset.lua", List.class);
    }

    /** 后台「模拟 N 用户并发」专用线程池 */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService simulatePool() {
        return Executors.newFixedThreadPool(64, r -> {
            Thread t = new Thread(r, "seckill-sim");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动时把 Lua 读成字符串（StaticScriptSource）。
     * 不能用 setLocation(ClassPathResource)：ResourceScriptSource 下每次 execute → getSha1() 都在全局锁内
     * 调 resource.lastModified()（fat jar 内是 JarURLConnection IO），所有 Lua 调用被串行化。
     * OM-025 压测线程快照：3000 QPS 拒绝路径下 375 个 Tomcat 线程 WAITING 在 DefaultRedisScript.getSha1。
     */
    private static <T> DefaultRedisScript<T> script(String path, Class<T> type) {
        try {
            String lua = StreamUtils.copyToString(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
            return new DefaultRedisScript<>(lua, type);
        } catch (IOException e) {
            throw new UncheckedIOException("load " + path, e);
        }
    }
}
