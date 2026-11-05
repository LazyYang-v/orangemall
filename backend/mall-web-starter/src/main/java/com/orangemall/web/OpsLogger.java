package com.orangemall.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangemall.common.Const;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 链路日志：写入 Redis List ops:logs（最多 200 条），后台「链路日志」页展示。
 * type: CACHE | MQ | PAY | SECKILL | ORDER | RISK。生产环境可替换为 SkyWalking / ELK。
 */
@Slf4j
@RequiredArgsConstructor
public class OpsLogger {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public void log(String type, String text) {
        log.info("[{}] {}", type, text);
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", Instant.now().toString());
            m.put("type", type);
            m.put("text", text);
            redis.opsForList().leftPush(Const.K_OPS_LOGS, mapper.writeValueAsString(m));
            redis.opsForList().trim(Const.K_OPS_LOGS, 0, 199);
        } catch (Exception e) {
            log.warn("ops log failed: {}", e.getMessage());
        }
    }
}
