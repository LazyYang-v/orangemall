package com.orangemall.common;

/** 业务异常：code 对应 docs/api-contract.md 第 10 节错误码 */
public class BizException extends RuntimeException {
    private final int code;

    public BizException(int code, String message) {
        super(message, null, false, false);
        this.code = code;
    }

    public static BizException of(int code, String message) {
        return new BizException(code, message);
    }

    public int getCode() {
        return code;
    }
}
