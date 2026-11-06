package com.orangemall.seckill.support;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OM-013：主题消费积压探测（committed offset 与 latest offset 之差）。
 * 用于区分「在途 > 0 是因为消息还在排队」（积压 → 只告警）与「没有任何待消费消息却仍有在途」（计数泄漏 → 可重置）。
 */
@Slf4j
@Component
public class KafkaLagProbe {
    private final KafkaAdmin kafkaAdmin;
    private final String groupId;
    private volatile AdminClient client;

    public KafkaLagProbe(KafkaAdmin kafkaAdmin, @Value("${spring.kafka.consumer.group-id}") String groupId) {
        this.kafkaAdmin = kafkaAdmin;
        this.groupId = groupId;
    }

    /** @return 该组在主题上的总积压；探测失败返回 -1（调用方按「未知」处理，不做自动重置） */
    public long lag(String topic) {
        try {
            AdminClient c = client();
            TopicDescription td = c.describeTopics(List.of(topic)).allTopicNames().get(3, TimeUnit.SECONDS).get(topic);
            Map<TopicPartition, OffsetSpec> req = new HashMap<>();
            td.partitions().forEach(p -> req.put(new TopicPartition(topic, p.partition()), OffsetSpec.latest()));
            var ends = c.listOffsets(req).all().get(3, TimeUnit.SECONDS);
            Map<TopicPartition, OffsetAndMetadata> committed =
                    c.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata().get(3, TimeUnit.SECONDS);
            long lag = 0;
            for (var e : ends.entrySet()) {
                OffsetAndMetadata om = committed.get(e.getKey());
                lag += Math.max(0, e.getValue().offset() - (om == null ? 0 : om.offset()));
            }
            return lag;
        } catch (Exception e) {
            log.warn("kafka lag probe failed: {}", e.toString());
            return -1;
        }
    }

    private AdminClient client() {
        AdminClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) client = AdminClient.create(kafkaAdmin.getConfigurationProperties());
                c = client;
            }
        }
        return c;
    }

    @PreDestroy
    void close() {
        if (client != null) client.close();
    }
}
