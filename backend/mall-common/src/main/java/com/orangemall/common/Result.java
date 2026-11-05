package com.orangemall.common;

import org.slf4j.MDC;

/**
 * 统一响应体：HTTP 状态码一律 200，业务结果看 code（0 = 成功）。
 * traceId 取自 MDC（由网关生成 X-Trace-Id 透传，服务端 TraceFilter 写入 MDC）。
 */
public class Result<T> {
    private int code;
    private String message;
    private T data;
    private String traceId;

    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = MDC.get(Const.MDC_TRACE);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }

    public static Result<Boolean> ok() {
        return new Result<>(0, "ok", Boolean.TRUE);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /** Feign 调用方使用：code≠0 时按原错误码抛出业务异常，便于错误码跨服务透传、Seata 全局事务回滚 */
    public T unwrap() {
        if (code != 0) throw new BizException(code, message);
        return data;
    }

    public boolean success() {
        return code == 0;
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
}
