package io.seata.testsupport;

/** 测试替身：web-starter 不依赖 Seata，GlobalExceptionHandler 按 io.seata.* 包名识别框架异常 */
public class TcUnavailableException extends RuntimeException {
    public TcUnavailableException(String msg) {
        super(msg);
    }
}
