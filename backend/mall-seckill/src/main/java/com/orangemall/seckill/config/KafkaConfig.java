package com.orangemall.seckill.config;

import com.orangemall.common.Const;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

/**
 * Kafka 拓扑与监听工厂。重试/补偿在消费者内部实现（SeckillOrderProcessor），
 * 这里只保证：String 反序列化、独立于 RabbitMQ 的 Jackson 转换器（避免 Boot 把
 * MessageConverter bean 误配给 Kafka）、DLT 主题预留。
 */
@Slf4j
@Configuration
public class KafkaConfig {
    public static final String DLT = Const.KAFKA_SECKILL_TOPIC + ".DLT";

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        var f = new ConcurrentKafkaListenerContainerFactory<String, String>();
        f.setConsumerFactory(consumerFactory);
        f.setConcurrency(3);
        // 消费者手动 ack（监听器内处理完再确认，失败重试由其内部实现）
        f.getContainerProperties().setAckMode(org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL);
        return f;
    }

    @Bean
    public NewTopic seckillTopic() {
        return TopicBuilder.name(Const.KAFKA_SECKILL_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic seckillDlt() {
        return TopicBuilder.name(DLT).partitions(3).replicas(1).build();
    }
}
