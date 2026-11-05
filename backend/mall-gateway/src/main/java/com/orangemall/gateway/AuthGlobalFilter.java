package com.orangemall.gateway;

import com.orangemall.common.Const;
import com.orangemall.common.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 统一鉴权：
 * 1. 生成 TraceId；
 * 2. 解析 Bearer token（WebSocket 握手用 ?token=），校验签名、过期与 Redis 黑名单；
 * 3. /api/admin/** 未登录 401、非管理员 403，直接在网关拦截；
 * 4. 清除外部伪造的 X-User-*，再写入可信的身份头透传给下游。
 * 其他接口是否需要登录由下游服务决定（optional 接口登录与否都可访问）。
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redis;
    private final String secret;

    public AuthGlobalFilter(ReactiveStringRedisTemplate redis, @Value("${jwt.secret}") String secret) {
        this.redis = redis;
        this.secret = secret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        String path = req.getURI().getRawPath();
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        boolean admin = path.startsWith("/api/admin/");

        String token = null;
        String auth = req.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith("Bearer ")) token = auth.substring(7).trim();
        if (token == null || token.isEmpty()) token = req.getQueryParams().getFirst("token");

        JwtUtil.Claims claims = token == null || token.isEmpty() ? null : JwtUtil.parse(token, secret);
        Mono<JwtUtil.Claims> identity = claims == null ? Mono.empty()
                : redis.hasKey(Const.K_TOKEN_BLACKLIST + claims.jti())
                .onErrorReturn(false)
                .flatMap(black -> black ? Mono.<JwtUtil.Claims>empty() : Mono.just(claims));

        return identity.map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty()).flatMap(opt -> {
            JwtUtil.Claims c = opt.orElse(null);
            if (admin) {
                if (c == null) return reject(exchange, 401, "请先登录", traceId);
                if (!"ADMIN".equals(c.role())) return reject(exchange, 403, "无权限访问", traceId);
            }
            ServerHttpRequest mutated = req.mutate().headers(h -> {
                h.remove(Const.H_USER_ID);
                h.remove(Const.H_USER_ROLE);
                h.set(Const.H_TRACE, traceId);
                if (c != null) {
                    h.set(Const.H_USER_ID, String.valueOf(c.userId()));
                    h.set(Const.H_USER_ROLE, c.role());
                }
            }).build();
            exchange.getResponse().getHeaders().set(Const.H_TRACE, traceId);
            return chain.filter(exchange.mutate().request(mutated).build());
        });
    }

    private Mono<Void> reject(ServerWebExchange exchange, int code, String msg, String traceId) {
        ServerHttpResponse resp = exchange.getResponse();
        resp.setStatusCode(HttpStatus.OK);
        resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":" + code + ",\"message\":\"" + msg + "\",\"data\":null,\"traceId\":\"" + traceId + "\"}";
        DataBuffer buf = resp.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return resp.writeWith(Mono.just(buf));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
