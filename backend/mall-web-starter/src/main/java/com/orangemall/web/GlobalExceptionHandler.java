package com.orangemall.web;

import com.orangemall.common.BizException;
import com.orangemall.common.Result;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 统一异常处理：HTTP 状态码一律 200，错误信息放在 Result.code / message */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> biz(BizException e) {
        // 业务拒绝（已抢完 / path 无效 / 限流…）属于正常流程：DEBUG 级别。
        // OM-025 压测实测：INFO 时秒杀拒绝路径每秒数千行同步写控制台，是 seckill 的主要 CPU 开销之一。
        log.debug("biz error {} {}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public Result<Void> badRequest(Exception e) {
        String msg = e instanceof MethodArgumentNotValidException ex && ex.getBindingResult().getFieldError() != null
                ? ex.getBindingResult().getFieldError().getDefaultMessage() : "请求参数错误";
        log.info("bad request: {}", e.getMessage());
        return Result.fail(400, msg);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> notFound(NoResourceFoundException e) {
        return Result.fail(404, "接口不存在");
    }

    @ExceptionHandler(FeignException.class)
    public Result<Void> feign(FeignException e) {
        log.error("downstream call failed: {}", e.getMessage());
        return Result.fail(503, "依赖服务暂不可用，请稍后再试");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> unknown(Exception e) {
        // Seata 等框架可能把业务异常包一层，向下找原始 BizException
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof BizException b) return biz(b);
        }
        log.error("unexpected error", e);
        return Result.fail(500, "服务器开小差了");
    }
}
