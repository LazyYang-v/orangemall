package com.orangemall.seckill.mq;

import com.orangemall.seckill.config.KafkaConfig;
import com.orangemall.seckill.entity.SeckillDeadLetter;
import com.orangemall.seckill.mapper.SeckillDeadLetterMapper;
import com.orangemall.web.OpsLogger;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * OM-019：把处理不了的秒杀消息投递到 DLT（带原因/异常/源位点头），由 {@link SeckillDltListener} 留档并告警。
 * DLT 投递失败时降级为直接落库（dlt_partition = -1 - 源分区，dlt_offset = 源 offset），保证死信不丢。
 */
@Slf4j
@Component
public class DeadLetterPublisher {
    public static final String H_REASON = "x-dlt-reason";
    public static final String H_ERROR = "x-dlt-error";
    public static final String H_SRC_PARTITION = "x-dlt-src-partition";
    public static final String H_SRC_OFFSET = "x-dlt-src-offset";
    public static final String H_ACTIVITY = "x-dlt-activity";
    public static final String H_USER = "x-dlt-user";

    private final KafkaTemplate<Object, Object> kafka;
    private final SeckillDeadLetterMapper mapper;
    private final OpsLogger ops;

    public DeadLetterPublisher(KafkaTemplate<Object, Object> kafka, SeckillDeadLetterMapper mapper, OpsLogger ops) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.ops = ops;
    }

    public void publish(ConsumerRecord<String, String> src, String reason, String error, String activityId, Long userId) {
        String err = error == null ? "" : (error.length() > 480 ? error.substring(0, 480) : error);
        ProducerRecord<Object, Object> rec = new ProducerRecord<>(KafkaConfig.DLT, src.key(), src.value());
        header(rec, H_REASON, reason);
        header(rec, H_ERROR, err);
        header(rec, H_SRC_PARTITION, String.valueOf(src.partition()));
        header(rec, H_SRC_OFFSET, String.valueOf(src.offset()));
        if (activityId != null) header(rec, H_ACTIVITY, activityId);
        if (userId != null) header(rec, H_USER, String.valueOf(userId));
        try {
            kafka.send(rec).get(5, TimeUnit.SECONDS);
            log.warn("seckill message -> DLT reason={} src={}-{}", reason, src.partition(), src.offset());
        } catch (Exception e) {
            // 降级：直接落库，仍可在后台「死信」中查到
            log.error("DLT send failed, fallback to DB: {}", e.toString());
            SeckillDeadLetter d = new SeckillDeadLetter();
            d.setActivityId(activityId);
            d.setUserId(userId);
            d.setReason(reason);
            d.setError(err + " | DLT 投递失败：" + e.getClass().getSimpleName());
            d.setPayload(src.value() == null ? "" : src.value());
            d.setSrcPartition(src.partition());
            d.setSrcOffset(src.offset());
            d.setDltPartition(-1 - src.partition());
            d.setDltOffset(src.offset());
            try {
                mapper.insertIgnore(d);
            } catch (Exception dbErr) {
                log.error("dead letter lost (DLT and DB both failed): {} {}", src.value(), dbErr.toString());
            }
            ops.log("ALERT", "[死信] DLT 投递失败，已直接落库：原因 " + reason + "，源位点 " + src.partition() + "-" + src.offset());
        }
    }

    private static void header(ProducerRecord<Object, Object> r, String k, String v) {
        r.headers().add(k, v.getBytes(StandardCharsets.UTF_8));
    }
}
