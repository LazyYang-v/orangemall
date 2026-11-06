package com.orangemall.order.config;

import com.orangemall.common.Const;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑（TTL + DLX 延迟关单）：
 *   order.delay.exchange → order.delay.30m.queue（队列 TTL 30min）/ 10m.queue（10min，秒杀单）
 *   到期死信 → order.close.exchange → order.close.queue（关单消费者）
 *   pay.exchange → order.pay.success.queue（支付成功）
 * 声明幂等：pay 服务也声明同一组 pay.*，参数保持一致。
 */
@Configuration
public class AmqpConfig {

    @Bean
    public Jackson2JsonMessageConverter jacksonConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter conv) {
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setMessageConverter(conv);
        return t;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory listenerFactory(ConnectionFactory cf, Jackson2JsonMessageConverter conv) {
        SimpleRabbitListenerContainerFactory f = new SimpleRabbitListenerContainerFactory();
        f.setConnectionFactory(cf);
        f.setMessageConverter(conv);
        f.setDefaultRequeueRejected(false);
        f.setPrefetchCount(20);
        return f;
    }

    @Bean
    public DirectExchange delayExchange() {
        return new DirectExchange(Const.MQ_ORDER_DELAY_EXCHANGE, true, false);
    }

    @Bean
    public Queue delay30m() {
        return QueueBuilder.durable(Const.MQ_ORDER_DELAY_30M_QUEUE)
                .withArgument("x-message-ttl", 30 * 60 * 1000)
                .withArgument("x-dead-letter-exchange", Const.MQ_ORDER_CLOSE_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", Const.MQ_ORDER_CLOSE_KEY)
                .build();
    }

    @Bean
    public Queue delay10m() {
        return QueueBuilder.durable(Const.MQ_ORDER_DELAY_10M_QUEUE)
                .withArgument("x-message-ttl", 10 * 60 * 1000)
                .withArgument("x-dead-letter-exchange", Const.MQ_ORDER_CLOSE_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", Const.MQ_ORDER_CLOSE_KEY)
                .build();
    }

    @Bean
    public Binding b30() {
        return BindingBuilder.bind(delay30m()).to(delayExchange()).with(Const.MQ_ORDER_DELAY_30M_KEY);
    }

    @Bean
    public Binding b10() {
        return BindingBuilder.bind(delay10m()).to(delayExchange()).with(Const.MQ_ORDER_DELAY_10M_KEY);
    }

    @Bean
    public DirectExchange closeExchange() {
        return new DirectExchange(Const.MQ_ORDER_CLOSE_EXCHANGE, true, false);
    }

    @Bean
    public Queue closeQueue() {
        return QueueBuilder.durable(Const.MQ_ORDER_CLOSE_QUEUE)
                .withArgument("x-dead-letter-exchange", Const.MQ_ORDER_CLOSE_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", Const.MQ_ORDER_CLOSE_KEY)
                .build();
    }

    @Bean
    public Binding bClose() {
        return BindingBuilder.bind(closeQueue()).to(closeExchange()).with(Const.MQ_ORDER_CLOSE_KEY);
    }

    /** close 队列消费失败死信（OM-003） */
    @Bean
    public DirectExchange closeDlx() {
        return new DirectExchange(Const.MQ_ORDER_CLOSE_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue closeDlq() {
        return QueueBuilder.durable(Const.MQ_ORDER_CLOSE_DLQ).build();
    }

    @Bean
    public Binding bCloseDlq() {
        return BindingBuilder.bind(closeDlq()).to(closeDlx()).with(Const.MQ_ORDER_CLOSE_KEY);
    }

    @Bean
    public DirectExchange payExchange() {
        return new DirectExchange(Const.MQ_PAY_EXCHANGE, true, false);
    }

    @Bean
    public Queue paySuccessQueue() {
        return QueueBuilder.durable(Const.MQ_PAY_SUCCESS_QUEUE)
                .withArgument("x-dead-letter-exchange", Const.MQ_PAY_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", Const.MQ_PAY_DLQ_KEY)
                .build();
    }

    /** pay 成功队列消费失败死信（OM-003） */
    @Bean
    public DirectExchange payDlx() {
        return new DirectExchange(Const.MQ_PAY_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue payDlq() {
        return QueueBuilder.durable(Const.MQ_PAY_DLQ).build();
    }

    @Bean
    public Binding bPay() {
        return BindingBuilder.bind(paySuccessQueue()).to(payExchange()).with(Const.MQ_PAY_SUCCESS_KEY);
    }

    @Bean
    public Binding bPayDlq() {
        return BindingBuilder.bind(payDlq()).to(payDlx()).with(Const.MQ_PAY_DLQ_KEY);
    }
}
