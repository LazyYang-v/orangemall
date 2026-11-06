package com.orangemall.seckill.mq;

import com.orangemall.seckill.config.KafkaConfig;
import com.orangemall.seckill.entity.SeckillDeadLetter;
import com.orangemall.seckill.mapper.SeckillDeadLetterMapper;
import com.orangemall.seckill.support.Keys;
import com.orangemall.web.OpsLogger;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * OM-019：DLT 监控型监听——只留档 + 告警，不自动重放（重试耗尽的消息已做过补偿，盲目重放会造成重复扣减）。
 * 独立 group，不与主消费者抢分区；落库按 DLT 位点幂等，异常抛给容器默认错误处理器重试。
 */
@Slf4j
@Component
public class SeckillDltListener {
    private final SeckillDeadLetterMapper mapper;
    private final StringRedisTemplate redis;
    private final OpsLogger ops;

    public SeckillDltListener(SeckillDeadLetterMapper mapper, StringRedisTemplate redis, OpsLogger ops) {
        this.mapper = mapper;
        this.redis = redis;
        this.ops = ops;
    }

    @KafkaListener(id = "seckillDlt", topics = KafkaConfig.DLT, groupId = "mall-seckill-dlt", concurrency = "1")
    public void onDeadLetter(ConsumerRecord<String, String> rec, Acknowledgment ack) {
        SeckillDeadLetter d = new SeckillDeadLetter();
        d.setReason(h(rec, DeadLetterPublisher.H_REASON, "UNKNOWN"));
        d.setError(h(rec, DeadLetterPublisher.H_ERROR, null));
        d.setActivityId(h(rec, DeadLetterPublisher.H_ACTIVITY, null));
        String uid = h(rec, DeadLetterPublisher.H_USER, null);
        d.setUserId(uid == null ? null : Long.valueOf(uid));
        String sp = h(rec, DeadLetterPublisher.H_SRC_PARTITION, null);
        String so = h(rec, DeadLetterPublisher.H_SRC_OFFSET, null);
        d.setSrcPartition(sp == null ? null : Integer.valueOf(sp));
        d.setSrcOffset(so == null ? null : Long.valueOf(so));
        d.setDltPartition(rec.partition());
        d.setDltOffset(rec.offset());
        d.setPayload(rec.value() == null ? "" : rec.value());
        if (mapper.insertIgnore(d) == 1) {
            redis.opsForHash().increment(Keys.METRICS, "deadLetters", 1);
            ops.log("ALERT", "[死信] 秒杀消息进入 DLT：原因 " + d.getReason()
                    + (d.getActivityId() == null ? "" : "，活动 " + d.getActivityId())
                    + (d.getUserId() == null ? "" : "，用户 " + d.getUserId())
                    + "，源位点 " + sp + "-" + so + (d.getError() == null || d.getError().isEmpty() ? "" : "，" + d.getError()));
        }
        ack.acknowledge();
    }

    private static String h(ConsumerRecord<String, String> r, String key, String def) {
        Header x = r.headers().lastHeader(key);
        if (x == null || x.value() == null || x.value().length == 0) return def;
        String v = new String(x.value(), StandardCharsets.UTF_8);
        return v.isBlank() ? def : v;
    }
}
