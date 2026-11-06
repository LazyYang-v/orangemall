package com.orangemall.pay.config;

import com.orangemall.common.Const;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 与订单服务共同幂等声明 pay 拓扑（参数一致，含 DLX 死信参数） */
@Configuration
public class AmqpConfig {

    @Bean
    public Jackson2JsonMessageConverter jacksonConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(org.springframework.amqp.rabbit.connection.ConnectionFactory cf,
                                         Jackson2JsonMessageConverter conv) {
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setMessageConverter(conv);
        return t;
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

    /** 死信交换机与 DLQ：消费失败的消息落此处（与 MallOrder 的 DlqListeners 联合重驱动） */
    @Bean
    public DirectExchange payDlx() {
        return new DirectExchange(Const.MQ_PAY_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue payDlq() {
        return QueueBuilder.durable(Const.MQ_PAY_DLQ).build();
    }

    @Bean
    public Binding bPayDlq() {
        return BindingBuilder.bind(payDlq()).to(payDlx()).with(Const.MQ_PAY_DLQ_KEY);
    }

    @Bean
    public Binding bPay() {
        return BindingBuilder.bind(paySuccessQueue()).to(payExchange()).with(Const.MQ_PAY_SUCCESS_KEY);
    }
}
