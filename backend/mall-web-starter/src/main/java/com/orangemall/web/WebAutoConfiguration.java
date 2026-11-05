package com.orangemall.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.orangemall.common.Const;
import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 所有 Servlet 微服务自动生效的公共配置。
 */
@AutoConfiguration
@Import(GlobalExceptionHandler.class)
@EnableFeignClients(basePackages = "com.orangemall.api.client")
public class WebAutoConfiguration {

    /**
     * Jackson：Long 一律序列化为字符串（雪花 ID 防 JS 精度丢失，所以非 ID 数值请用 int）；
     * 时间 ISO-8601；null 字段不输出（与前端可选字段 undefined 语义一致）。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer mallJackson() {
        return b -> b.serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    }

    @Bean
    public FilterRegistrationBean<ContextFilter> mallContextFilter() {
        FilterRegistrationBean<ContextFilter> r = new FilterRegistrationBean<>(new ContextFilter());
        r.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return r;
    }

    /** Feign 调用透传用户身份与 traceId（Seata XID 由 seata starter 自动透传） */
    @Bean
    public RequestInterceptor mallFeignInterceptor() {
        return t -> {
            if (UserContext.userId() != null) t.header(Const.H_USER_ID, String.valueOf(UserContext.userId()));
            if (UserContext.role() != null) t.header(Const.H_USER_ROLE, UserContext.role());
            if (UserContext.traceId() != null) t.header(Const.H_TRACE, UserContext.traceId());
        };
    }

    @Bean
    public OpsLogger opsLogger(StringRedisTemplate redis, ObjectMapper mapper) {
        return new OpsLogger(redis, mapper);
    }

    @Bean
    public Notifier notifier(StringRedisTemplate redis, ObjectMapper mapper) {
        return new Notifier(redis, mapper);
    }
}
