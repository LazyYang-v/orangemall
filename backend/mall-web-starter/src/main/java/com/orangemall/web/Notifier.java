package com.orangemall.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangemall.common.Const;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WebSocket 推送总线：任何服务把消息发布到 Redis 频道 mall:notify，
 * 订单服务（持有 /ws/notify 连接）订阅后推给对应用户。消息体见 frontend/src/types/notify.ts。
 */
@Slf4j
@RequiredArgsConstructor
public class Notifier {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    /** @param payload 如 {type:'ORDER_STATUS', orderId, status, text}（orderId 等 ID 传字符串） */
    public void send(long userId, Map<String, Object> payload) {
        try {
            Map<String, Object> env = new LinkedHashMap<>();
            env.put("userId", String.valueOf(userId));
            env.put("payload", payload);
            redis.convertAndSend(Const.CH_NOTIFY, mapper.writeValueAsString(env));
        } catch (Exception e) {
            log.warn("notify failed: {}", e.getMessage());
        }
    }

    public void orderStatus(long userId, long orderId, String status, String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "ORDER_STATUS");
        m.put("orderId", String.valueOf(orderId));
        m.put("status", status);
        m.put("text", text);
        send(userId, m);
    }
}
