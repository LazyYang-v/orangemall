package com.orangemall.order.mq;

import com.orangemall.api.dto.OrderCloseMsg;
import com.orangemall.api.dto.PaySuccessMsg;
import com.orangemall.common.Const;
import com.orangemall.order.service.OrderService;
import com.orangemall.web.OpsLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * MQ 消费者：关单（DLX 死信）与支付成功。失败不重入队 —— 关单有定时扫描兜底，
 * 支付成功消息丢失会导致订单停留在待支付（沙箱演示环境可接受，重发回调即可恢复）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqListeners {
    private final OrderService orders;
    private final OpsLogger ops;

    @RabbitListener(queues = Const.MQ_ORDER_CLOSE_QUEUE, containerFactory = "listenerFactory")
    public void onClose(OrderCloseMsg msg) {
        try {
            orders.closeById(msg.orderId(), "支付超时，订单自动关闭", com.orangemall.order.domain.OrderStatus.CLOSED);
            log.info("order closed: {}", msg.orderId());
        } catch (Exception e) {
            log.error("close consumer failed for {}: {}", msg.orderId(), e.getMessage());
            ops.log("MQ", "[关单消费失败] 订单 " + msg.orderId() + "：" + e.getMessage());
        }
    }

    @RabbitListener(queues = Const.MQ_PAY_SUCCESS_QUEUE, containerFactory = "listenerFactory")
    public void onPaySuccess(PaySuccessMsg msg) {
        try {
            orders.onPaySuccess(msg);
            log.info("pay success consumed: {} -> {}", msg.paymentNo(), msg.orderId());
        } catch (Exception e) {
            log.error("pay success consumer failed: {}", e.getMessage());
            ops.log("MQ", "[支付成功消费失败] 流水 " + msg.paymentNo() + "：" + e.getMessage());
        }
    }
}
