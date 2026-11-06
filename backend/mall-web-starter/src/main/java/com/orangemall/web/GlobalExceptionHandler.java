package com.orangemall.web;

import com.orangemall.common.BizException;
import com.orangemall.common.Result;
import feign.FeignException;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 统一异常处理：HTTP 状态码一律 200，错误信息放在 Result.code / message。
 * 因此 http_server_requests 的 5xx 永远为空，错误率改由 {@code mall_api_errors_total{kind,code}} 观测（OM-020）：
 * kind = biz（业务拒绝，正常流程）/ bad_request / transient（依赖或 TC 暂不可用，可重试）/ unexpected（真正的缺陷）。
 * 计数挂在 Micrometer 全局注册表上（Spring Boot 默认把自身注册表加入其中），单测里是无副作用的空实现。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static Result<Void> fail(String kind, int code, String message) {
        Metrics.counter("mall.api.errors", "kind", kind, "code", String.valueOf(code)).increment();
        return Result.fail(code, message);
    }

    @ExceptionHandler(BizException.class)
    public Result<Void> biz(BizException e) {
        // 业务拒绝（已抢完 / path 无效 / 限流…）属于正常流程：DEBUG 级别。
        // OM-025 压测实测：INFO 时秒杀拒绝路径每秒数千行同步写控制台，是 seckill 的主要 CPU 开销之一。
        log.debug("biz error {} {}", e.getCode(), e.getMessage());
        return fail("biz", e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public Result<Void> badRequest(Exception e) {
        String msg = e instanceof MethodArgumentNotValidException ex && ex.getBindingResult().getFieldError() != null
                ? ex.getBindingResult().getFieldError().getDefaultMessage() : "请求参数错误";
        log.info("bad request: {}", e.getMessage());
        return fail("bad_request", 400, msg);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> notFound(NoResourceFoundException e) {
        return fail("bad_request", 404, "接口不存在");
    }

    @ExceptionHandler(FeignException.class)
    public Result<Void> feign(FeignException e) {
        log.error("downstream call failed: {}", e.getMessage());
        return fail("transient", 503, "依赖服务暂不可用，请稍后再试");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> unknown(Exception e) {
        // Seata 等框架可能把业务异常包一层，向下找原始 BizException
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof BizException b) return biz(b);
        }
        // OM-017：TC 重启 / 不可达、全局回滚上报失败等 Seata 框架异常，以及被包装的下游调用失败，都是可重试的暂时不可用：
        // 数据由 TC 恢复后超时回滚兜底（见 bench/drills/seata-restart.sh），对用户给明确的 503 而不是「服务器开小差」。
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof FeignException || t.getClass().getName().startsWith("io.seata.")) {
                log.warn("transient failure: {} ← {}", e.toString(), t.toString());
                return fail("transient", 503, "系统繁忙，请稍后重试");
            }
        }
        log.error("unexpected error", e);
        return fail("unexpected", 500, "服务器开小差了");
    }
}
