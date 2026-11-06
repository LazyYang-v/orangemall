package com.orangemall.seckill.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangemall.api.client.OrderInnerClient;
import com.orangemall.api.dto.SeckillOrderReq;
import com.orangemall.seckill.mapper.SeckillActivityMapper;
import com.orangemall.seckill.mapper.SeckillOrderMapper;
import com.orangemall.seckill.entity.SeckillOrder;
import com.orangemall.seckill.service.SeckillDeductBatcher;
import com.orangemall.seckill.support.Keys;
import com.orangemall.web.Notifier;
import com.orangemall.web.OpsLogger;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 第四层：Kafka 消费者 —— 落 seckill_order（幂等 uk）+ DB 乐观锁扣减（合并器同事务批量执行）→ Feign 建秒杀订单（幂等 orderId）
 * → finish.lua（在途-1、结果置 SUCCESS）→ WebSocket 推送 SECKILL_RESULT。
 * 监听器内部自行重试（1s→2s→4s→8s×2，共约 23s），耗尽后补偿（回补 Redis/DB 库存、结果置 FAILED）并 ack。
 * 全程不向容器抛异常，保证分区不被阻塞。
 * OM-019：非 JSON（POISON）、缺关键字段（INVALID）、重试耗尽（EXHAUSTED）、补偿失败（COMPENSATE_FAILED）
 * 一律投递 DLT 留档告警，不再静默丢弃。
 */
@Slf4j
@Component
public class SeckillOrderProcessor {
    /** 监听器 id：后台演练可暂停/恢复主消费者（{@code KafkaListenerEndpointRegistry}） */
    public static final String LISTENER_ID = "seckillOrder";
    private static final long[] BACKOFF_MS = {1000, 2000, 4000, 8000, 8000};

    private final SeckillActivityMapper activityMapper;
    private final SeckillOrderMapper orderMapper;
    private final OrderInnerClient orders;
    private final StringRedisTemplate redis;
    private final ObjectMapper om;
    private final Notifier notifier;
    private final OpsLogger ops;
    private final DefaultRedisScript<Long> finishScript;
    private final DeadLetterPublisher dlt;
    private final SeckillDeductBatcher deductBatcher;

    public SeckillOrderProcessor(SeckillActivityMapper activityMapper, SeckillOrderMapper orderMapper,
                                 OrderInnerClient orders, StringRedisTemplate redis, ObjectMapper om,
                                 Notifier notifier, OpsLogger ops, DefaultRedisScript<Long> finishScript,
                                 DeadLetterPublisher dlt, SeckillDeductBatcher deductBatcher) {
        this.activityMapper = activityMapper;
        this.orderMapper = orderMapper;
        this.orders = orders;
        this.redis = redis;
        this.om = om;
        this.notifier = notifier;
        this.ops = ops;
        this.finishScript = finishScript;
        this.dlt = dlt;
        this.deductBatcher = deductBatcher;
    }

    /*
     * idIsGroup = false 必须保留：spring-kafka 默认把 listener id 当作 group.id，
     * 一旦 id 与 spring.kafka.consumer.group-id（mall-seckill）不同，就会以新消费组 + earliest 从头重放整个主题。
     */
    @KafkaListener(id = LISTENER_ID, idIsGroup = false, topics = com.orangemall.common.Const.KAFKA_SECKILL_TOPIC)
    public void onMessage(ConsumerRecord<String, String> rec, Acknowledgment ack) {
        String value = rec.value();
        JsonNode m;
        try {
            m = om.readTree(value);
            if (m == null || !m.isObject()) throw new IllegalArgumentException("not a JSON object");
        } catch (Exception e) {
            log.error("seckill message not JSON -> DLT: {}", value);
            dlt.publish(rec, "POISON", e.toString(), null, null);
            ack.acknowledge();
            return;
        }
        String invalid = validate(m);
        if (invalid != null) {
            log.error("seckill message invalid ({}) -> DLT: {}", invalid, value);
            dlt.publish(rec, "INVALID", invalid, textOrNull(m, "activityId"), longOrNull(m, "userId"));
            ack.acknowledge();
            return;
        }
        boolean sim = "true".equals(m.path("sim").asText("false"));
        Exception last = null;
        for (int attempt = 0; attempt <= BACKOFF_MS.length; attempt++) {
            try {
                process(m, sim);
                ack.acknowledge();
                return;
            } catch (Exception e) {
                last = e;
                log.warn("seckill consume attempt {} failed: {}", attempt + 1, e.toString());
                if (attempt < BACKOFF_MS.length) {
                    try {
                        Thread.sleep(BACKOFF_MS[attempt]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        // 重试耗尽：补偿并留档（补偿失败 → 在途计数可能泄漏，由 SeckillJob 滞留巡检兜底）
        log.error("seckill consume retries exhausted: {}", value, last);
        String aid = m.get("activityId").asText();
        long uid = m.get("userId").asLong();
        String cause = last == null ? "unknown" : last.toString();
        try {
            onExhausted(m, sim, last);
            dlt.publish(rec, "EXHAUSTED", "重试 " + (BACKOFF_MS.length + 1) + " 次失败，已补偿：" + cause, aid, uid);
        } catch (Exception e) {
            log.error("compensate failed: {}", e.getMessage());
            dlt.publish(rec, "COMPENSATE_FAILED", "补偿失败：" + e + "；原始异常：" + cause, aid, uid);
        }
        ack.acknowledge();
    }

    /** 关键字段校验：返回 null 表示合法，否则为原因 */
    static String validate(JsonNode m) {
        String aid = textOrNull(m, "activityId");
        if (aid == null || aid.isBlank()) return "缺少 activityId";
        if (longOrNull(m, "userId") == null) return "缺少或非法 userId";
        if (longOrNull(m, "orderId") == null) return "缺少或非法 orderId";
        return null;
    }

    private static String textOrNull(JsonNode m, String f) {
        JsonNode n = m.get(f);
        return n == null || n.isNull() || !n.isValueNode() ? null : n.asText();
    }

    private static Long longOrNull(JsonNode m, String f) {
        JsonNode n = m.get(f);
        if (n == null || n.isNull()) return null;
        if (n.canConvertToLong()) return n.asLong();
        if (n.isTextual() && n.asText().matches("-?\\d{1,19}")) return Long.parseLong(n.asText());
        return null;
    }

    void process(JsonNode m, boolean sim) {
        String activityId = m.get("activityId").asText();
        long userId = m.get("userId").asLong();
        long orderId = m.get("orderId").asLong();
        long skuId = m.path("skuId").asLong();
        int seckillPrice = m.path("seckillPrice").asInt();
        Long addressId = m.path("addressId").isMissingNode() || m.path("addressId").isNull()
                ? null : m.path("addressId").asLong();

        // 模拟请求（后台压测）：只扣 DB 库存与记指标，不创建真实订单（对齐 Mock simulate 语义）
        if (sim) {
            if (activityMapper.decrStock(activityId) == 0) {
                finish(activityId, userId, json("FAILED", "库存不足（DB 乐观锁兜底拦截）"), "1", "1", "optimisticFail", true, null);
            } else {
                finish(activityId, userId, json("SUCCESS", null), "0", "0", "ordersCreated", true, null);
            }
            return;
        }

        // 记成交 + DB 乐观锁扣减：合并器内同一本地事务批量执行（OM-025）。
        // 幂等：该用户已有成交记录（重试恢复）返回 true 且不重复扣减，直接补齐 Feign 建单
        if (!deductBatcher.deduct(activityId, userId, orderId)) {
            ops.log("SECKILL", "[消费] " + activityId + " DB 乐观锁兜底拦截 userId=" + userId);
            finish(activityId, userId, json("FAILED", "库存不足（DB 乐观锁兜底拦截）"), "1", "1", "optimisticFail", sim, null);
            return;
        }
        Long created = orders.createSeckill(new SeckillOrderReq(orderId, userId, activityId, skuId,
                seckillPrice, addressId)).unwrap();
        // 不再为日志单独查 DB 剩余库存（每单多一次 SELECT，OM-025）；剩余库存见后台活动页 / 一致性巡检
        ops.log("SECKILL", "[消费] 订单 " + created + " 落库成功，ACK 消息");
        finish(activityId, userId, json("SUCCESS", String.valueOf(created)), "0", "0", "ordersCreated", sim, created);
    }

    /** 重试耗尽：补偿（回补 Redis/DB 库存、移出已购集合、结果置 FAILED） */
    private void onExhausted(JsonNode m, boolean sim, Exception cause) {
        String activityId = m.get("activityId").asText();
        long userId = m.get("userId").asLong();
        SeckillOrder existing = orderMapper.find(activityId, userId);
        if (existing != null) {
            activityMapper.incrStock(activityId);
            orderMapper.deleteOne(activityId, userId, existing.getOrderId());
        }
        finish(activityId, userId, json("FAILED", "系统繁忙，请重试"), "1", "1", "", sim, null);
        ops.log("SECKILL", "[重试耗尽] " + activityId + " userId=" + userId + "：" + cause.getMessage());
    }

    private void finish(String activityId, long userId, String resultJson, String restore, String removeBought,
                        String metricField, boolean sim, Long orderId) {
        redis.execute(finishScript,
                List.of(Keys.stock(activityId), Keys.bought(activityId), Keys.inflight(activityId),
                        Keys.METRICS, Keys.result(activityId, userId)),
                String.valueOf(userId), resultJson, "86400", restore, removeBought, metricField);
        if (!sim && orderId != null) {
            Map<String, Object> payload = Map.of("type", "SECKILL_RESULT", "activityId", activityId,
                    "orderId", String.valueOf(orderId), "success", true);
            notifier.send(userId, payload);
        }
    }

    private String json(String state, String v) {
        try {
            if ("SUCCESS".equals(state)) {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("state", "SUCCESS");
                if (v != null) m.put("orderId", v);
                return om.writeValueAsString(m);
            }
            return om.writeValueAsString(Map.of("state", "FAILED", "reason", v));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
