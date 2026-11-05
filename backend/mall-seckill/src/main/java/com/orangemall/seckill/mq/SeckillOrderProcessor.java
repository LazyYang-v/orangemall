package com.orangemall.seckill.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangemall.api.client.OrderInnerClient;
import com.orangemall.api.dto.SeckillOrderReq;
import com.orangemall.seckill.mapper.SeckillActivityMapper;
import com.orangemall.seckill.mapper.SeckillOrderMapper;
import com.orangemall.seckill.entity.SeckillOrder;
import com.orangemall.seckill.support.Keys;
import com.orangemall.web.Notifier;
import com.orangemall.web.OpsLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 第四层：Kafka 消费者 —— DB 乐观锁扣减 → 落 seckill_order（幂等 uk）→ Feign 建秒杀订单（幂等 orderId）
 * → finish.lua（在途-1、结果置 SUCCESS）→ WebSocket 推送 SECKILL_RESULT。
 * 监听器内部自行重试（1s→2s→4s→8s×2，共约 23s），耗尽后补偿（回补 Redis/DB 库存、结果置 FAILED）并 ack。
 * 全程不向容器抛异常，保证分区不被阻塞。
 */
@Slf4j
@Component
public class SeckillOrderProcessor {
    private static final long[] BACKOFF_MS = {1000, 2000, 4000, 8000, 8000};

    private final SeckillActivityMapper activityMapper;
    private final SeckillOrderMapper orderMapper;
    private final OrderInnerClient orders;
    private final StringRedisTemplate redis;
    private final ObjectMapper om;
    private final Notifier notifier;
    private final OpsLogger ops;
    private final DefaultRedisScript<Long> finishScript;

    public SeckillOrderProcessor(SeckillActivityMapper activityMapper, SeckillOrderMapper orderMapper,
                                 OrderInnerClient orders, StringRedisTemplate redis, ObjectMapper om,
                                 Notifier notifier, OpsLogger ops, DefaultRedisScript<Long> finishScript) {
        this.activityMapper = activityMapper;
        this.orderMapper = orderMapper;
        this.orders = orders;
        this.redis = redis;
        this.om = om;
        this.notifier = notifier;
        this.ops = ops;
        this.finishScript = finishScript;
    }

    @KafkaListener(topics = com.orangemall.common.Const.KAFKA_SECKILL_TOPIC)
    public void onMessage(String value, Acknowledgment ack) {
        JsonNode m;
        try {
            m = om.readTree(value);
        } catch (Exception e) {
            log.error("seckill message not JSON, drop: {}", value);
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
        // 重试耗尽：补偿并留档
        log.error("seckill consume retries exhausted: {}", value, last);
        try {
            onExhausted(m, sim, last);
        } catch (Exception e) {
            log.error("compensate failed: {}", e.getMessage());
        }
        ack.acknowledge();
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

        // 幂等：同 orderId 已落库（重试恢复）直接补齐 Feign 建单
        SeckillOrder existing = orderMapper.find(activityId, userId);
        if (existing == null) {
            int rows = activityMapper.decrStock(activityId);
            if (rows == 0) {
                ops.log("SECKILL", "[消费] " + activityId + " DB 乐观锁兜底拦截 userId=" + userId);
                finish(activityId, userId, json("FAILED", "库存不足（DB 乐观锁兜底拦截）"), "1", "1", "optimisticFail", sim, null);
                return;
            }
            SeckillOrder so = new SeckillOrder();
            so.setActivityId(activityId);
            so.setUserId(userId);
            so.setOrderId(orderId);
            try {
                orderMapper.insert(so);
            } catch (DuplicateKeyException dup) {
                log.info("duplicate seckill_order {} {}", activityId, userId);
            }
        }
        Long created = orders.createSeckill(new SeckillOrderReq(orderId, userId, activityId, skuId,
                seckillPrice, addressId)).unwrap();
        ops.log("SECKILL", "[消费] 订单 " + created + " 落库成功，ACK 消息；DB 库存剩余 " + activityMapper.stockOf(activityId));
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
