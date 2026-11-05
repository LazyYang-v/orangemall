package com.orangemall.gateway;

import com.orangemall.common.Const;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关自身异常（下游无实例、连接超时、路由不存在等）统一转成 Result 结构，
 * 保持「HTTP 一律 200、业务码看 code」的约定，前端 http.ts 可统一提示。
 */
@Component
@Order(-2)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse resp = exchange.getResponse();
        if (resp.isCommitted()) return Mono.error(ex);
        int status = ex instanceof ResponseStatusException rse ? rse.getStatusCode().value() : 500;
        int code;
        String msg;
        if (status == 404) {
            code = 404;
            msg = "接口不存在";
        } else if (status == 503 || status == 504 || ex instanceof java.net.ConnectException
                || ex instanceof java.util.concurrent.TimeoutException) {
            code = 503;
            msg = "服务繁忙，请稍后再试";
        } else {
            code = 500;
            msg = "服务器开小差了";
        }
        log.warn("gateway error {} {} -> {}", exchange.getRequest().getURI().getPath(), ex.toString(), code);
        String traceId = exchange.getRequest().getHeaders().getFirst(Const.H_TRACE);
        if (traceId == null) traceId = resp.getHeaders().getFirst(Const.H_TRACE);
        String body = "{\"code\":" + code + ",\"message\":\"" + msg + "\",\"data\":null"
                + (traceId != null ? ",\"traceId\":\"" + traceId + "\"" : "") + "}";
        resp.setStatusCode(HttpStatus.OK);
        resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buf = resp.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return resp.writeWith(Mono.just(buf));
    }
}
