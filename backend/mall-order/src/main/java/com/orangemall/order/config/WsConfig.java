package com.orangemall.order.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangemall.common.Const;
import com.orangemall.order.service.NotifyPushService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** WebSocket /ws/notify 注册 + Redis 推送订阅 */
@Configuration
@EnableWebSocket
public class WsConfig implements WebSocketConfigurer {
    private final NotifyPushService handler;
    private final ObjectMapper om;
    private final String secret;

    public WsConfig(NotifyPushService handler, ObjectMapper om, @Value("${jwt.secret}") String secret) {
        this.handler = handler;
        this.om = om;
        this.secret = secret;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/notify")
                .addInterceptors(new NotifyPushService.AuthInterceptor(handler, om, secret))
                .setAllowedOriginPatterns("*");
    }

    @Bean
    public RedisMessageListenerContainer notifyContainer(RedisConnectionFactory cf, NotifyPushService svc) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(cf);
        container.addMessageListener((message, pattern) -> {
            try {
                svc.onNotify(new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(WsConfig.class).warn("notify dispatch failed: {}", e.getMessage());
            }
        }, new ChannelTopic(Const.CH_NOTIFY));
        return container;
    }
}
