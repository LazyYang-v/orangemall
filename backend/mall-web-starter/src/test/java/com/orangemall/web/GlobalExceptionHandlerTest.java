package com.orangemall.web;

import com.orangemall.common.BizException;
import feign.FeignException;
import feign.Request;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 被框架（Seata / 动态代理）包装的异常也要映射到正确的业务码，而不是一律 500 */
class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler h = new GlobalExceptionHandler();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    {
        Metrics.addRegistry(registry);
    }

    @AfterEach
    void tearDown() {
        Metrics.removeRegistry(registry);
    }

    private double errors(String kind, String code) {
        var c = registry.find("mall.api.errors").tags("kind", kind, "code", code).counter();
        return c == null ? 0 : c.count();
    }

    @Test
    void unwrapsBizExceptionThroughFrameworkWrapper() {
        var r = h.unknown(new RuntimeException("try to proceed invocation error", new BizException(5001, "请勿重复提交订单")));
        assertThat(r.getCode()).isEqualTo(5001);
        assertThat(errors("biz", "5001")).isEqualTo(1);
    }

    @Test
    void wrappedDownstreamFailureIsTransient503() {
        Request req = Request.create(Request.HttpMethod.POST, "http://mall-product/inner/stock/deduct", Map.of(), null, StandardCharsets.UTF_8, null);
        var feign = new FeignException.ServiceUnavailable("No available service", req, null, Map.of());
        var r = h.unknown(new RuntimeException("try to proceed invocation error", feign));
        assertThat(r.getCode()).isEqualTo(503);
    }

    @Test
    void seataFrameworkFailureIsTransient503() {
        var r = h.unknown(new RuntimeException(new io.seata.testsupport.TcUnavailableException("can not register RM,err:can not connect to services-server.")));
        assertThat(r.getCode()).isEqualTo(503);
        assertThat(errors("transient", "503")).isEqualTo(1);
    }

    @Test
    void genuineBugStays500() {
        assertThat(h.unknown(new NullPointerException()).getCode()).isEqualTo(500);
        assertThat(errors("unexpected", "500")).isEqualTo(1);
    }
}
