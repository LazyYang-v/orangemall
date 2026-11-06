package com.orangemall.seckill.support;

import com.orangemall.seckill.service.SeckillReliabilityService;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OM-020：秒杀业务指标导出到 Prometheus（/actuator/prometheus）。
 *
 * <p>漏斗计数器 {@code seckill_funnel_total{stage}} 直接映射 Redis {@code seckill:metrics} Hash（Lua 原子累加，
 * 多实例共享同一份，Grafana 里不要按 instance 求和）；在途 / 主题积压 / 死信 / 消费者状态取自可靠性健康视图。
 * 采样由定时任务每 5s 做一次，抓取（scrape）本身只读内存值，不放大 Redis / Kafka / DB 访问。
 */
@Slf4j
@Component
public class SeckillMetrics {
    /** 与 Lua / 消费者写入的字段一一对应：请求 → 限流/非法 path/重复/售罄 → 入队 → 落单 / DB 兜底 / 死信 */
    static final List<String> FUNNEL = List.of("totalRequests", "rateLimited", "illegalPath", "repeatBlocked", "soldOut",
            "enqueued", "ordersCreated", "optimisticFail", "deadLetters", "inflightResets");

    private final StringRedisTemplate redis;
    private final SeckillReliabilityService reliability;
    private final Map<String, AtomicLong> funnel = new LinkedHashMap<>();
    private final AtomicLong lag = new AtomicLong(-1);
    private final AtomicLong inflight = new AtomicLong();
    private final AtomicLong deadNew = new AtomicLong();
    private final AtomicLong deadTotal = new AtomicLong();
    private final AtomicLong running = new AtomicLong();
    private final AtomicLong paused = new AtomicLong();

    public SeckillMetrics(MeterRegistry registry, StringRedisTemplate redis, SeckillReliabilityService reliability) {
        this.redis = redis;
        this.reliability = reliability;
        for (String stage : FUNNEL) {
            AtomicLong v = new AtomicLong();
            funnel.put(stage, v);
            FunctionCounter.builder("seckill.funnel", v, AtomicLong::get).tag("stage", stage)
                    .description("秒杀漏斗累计计数（Redis seckill:metrics）").register(registry);
        }
        Gauge.builder("seckill.kafka.lag", lag, AtomicLong::get).description("seckill-order 主题消费积压（-1 = 探测失败）").register(registry);
        Gauge.builder("seckill.inflight", inflight, AtomicLong::get).description("已在 Redis 扣减、尚未落单完成的消息数（当前场次合计）").register(registry);
        Gauge.builder("seckill.dead.letters", deadNew, AtomicLong::get).tag("status", "new").description("待处理死信").register(registry);
        Gauge.builder("seckill.dead.letters", deadTotal, AtomicLong::get).tag("status", "total").description("死信总数").register(registry);
        Gauge.builder("seckill.consumer.running", running, AtomicLong::get).description("落单消费者运行中=1").register(registry);
        Gauge.builder("seckill.consumer.paused", paused, AtomicLong::get).description("落单消费者被暂停=1（演练开关）").register(registry);
    }

    @Scheduled(initialDelay = 5_000, fixedDelay = 5_000)
    @SuppressWarnings("unchecked")
    public void sample() {
        try {
            Map<Object, Object> raw = redis.opsForHash().entries(Keys.METRICS);
            funnel.forEach((stage, v) -> v.set(parse(raw.get(stage))));

            Map<String, Object> h = reliability.health();
            lag.set(((Number) h.get("lag")).longValue());
            long sum = 0;
            for (Map<String, Object> row : (List<Map<String, Object>>) h.get("inflight")) sum += ((Number) row.get("inflight")).longValue();
            inflight.set(sum);
            Map<String, Object> dl = (Map<String, Object>) h.get("deadLetters");
            deadNew.set(((Number) dl.get("new")).longValue());
            deadTotal.set(((Number) dl.get("total")).longValue());
            Map<String, Object> c = (Map<String, Object>) h.get("consumer");
            running.set(Boolean.TRUE.equals(c.get("running")) ? 1 : 0);
            paused.set(Boolean.TRUE.equals(c.get("paused")) ? 1 : 0);
        } catch (RuntimeException e) {
            log.debug("seckill metrics sample failed: {}", e.toString());
        }
    }

    private static long parse(Object v) {
        try {
            return v == null ? 0 : Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
