package com.orangemall.order.service;

import com.orangemall.common.Const;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangemall.common.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WebSocket 实时推送：/ws/notify?token=（网关也会在握手时透传 X-User-Id）。
 * 所有服务的 Notifier → Redis 频道 mall:notify → 这里订阅并按 userId 分发给在线会话。
 * 多实例部署时每实例都订阅频道，天然支持跨实例投递。
 */
@Slf4j
@Component
public class NotifyPushService extends TextWebSocketHandler {
    private static final Map<Long, List<WebSocketSession>> SESSIONS = new ConcurrentHashMap<>();

    private final ObjectMapper om;
    private final String secret;

    public NotifyPushService(ObjectMapper om, @Value("${jwt.secret}") String secret) {
        this.om = om;
        this.secret = secret;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long uid = userId(session);
        if (uid == null) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException ignored) {
            }
            return;
        }
        SESSIONS.computeIfAbsent(uid, k -> new CopyOnWriteArrayList<>()).add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long uid = userId(session);
        if (uid != null) {
            List<WebSocketSession> list = SESSIONS.get(uid);
            if (list != null) {
                list.remove(session);
                if (list.isEmpty()) SESSIONS.remove(uid);
            }
        }
    }

    private Long userId(WebSocketSession session) {
        Object attr = session.getAttributes().get("userId");
        if (attr instanceof Long l) return l;
        Object header = session.getAttributes().get("headerUserId");
        return header instanceof Long l ? l : null;
    }

    public static class AuthInterceptor implements org.springframework.web.socket.server.HandshakeInterceptor {
        private final NotifyPushService svc;
        private final ObjectMapper om;
        private final String secret;

        public AuthInterceptor(NotifyPushService svc, ObjectMapper om, String secret) {
            this.svc = svc;
            this.om = om;
            this.secret = secret;
        }

        @Override
        public boolean beforeHandshake(org.springframework.http.server.ServerHttpRequest request,
                                       org.springframework.http.server.ServerHttpResponse response,
                                       org.springframework.web.socket.WebSocketHandler wsHandler,
                                       Map<String, Object> attributes) {
            String uid = request.getHeaders().getFirst(Const.H_USER_ID);
            if (uid != null) {
                attributes.put("headerUserId", Long.parseLong(uid));
                return true;
            }
            // 网关未透传时自行校验 token（多实例/直连场景）
            String token = org.springframework.web.util.UriComponentsBuilder.fromUri(request.getURI()).build()
                    .getQueryParams().getFirst("token");
            if (token != null) {
                JwtUtil.Claims c = JwtUtil.parse(token, secret);
                if (c != null) {
                    attributes.put("userId", c.userId());
                    return true;
                }
            }
            return false;
        }

        @Override
        public void afterHandshake(org.springframework.http.server.ServerHttpRequest request,
                                   org.springframework.http.server.ServerHttpResponse response,
                                   org.springframework.web.socket.WebSocketHandler wsHandler, Exception exception) {
        }
    }

    /** MessageListenerAdapter 回调：入参为 String 时直接调用本方法 */
    public void onNotify(String message) {
        try {
            JsonNode env = om.readTree(message);
            long uid = Long.parseLong(env.get("userId").asText());
            List<WebSocketSession> list = SESSIONS.get(uid);
            if (list == null || list.isEmpty()) return;
            String payload = om.writeValueAsString(env.get("payload"));
            for (WebSocketSession s : list) {
                try {
                    synchronized (s) {
                        s.sendMessage(new TextMessage(payload));
                    }
                } catch (Exception e) {
                    log.warn("ws push failed: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("notify parse failed: {}", e.getMessage());
        }
    }
}
