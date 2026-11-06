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
 * 业务死信兜底消费（OM-003）：close / pay.success 队列消费抛异常的消息经队列级 DLX 落入 DLQ，
 * 此处原样重驱动。两侧处理器都幂等（关单 CAS 仅 PENDING_PAYMENT；markPaid CAS 仅 PENDING），
 * 重驱动失败不回灌 DLQ（直接 ack 并留 ops 日志），避免坏消息造成无限死循环。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DlqListeners {
    private final OrderService orders;
    private final OpsLogger ops;

    @RabbitListener(queues = Const.MQ_ORDER_CLOSE_DLQ, containerFactory = "listenerFactory")
    public void onCloseDlq(OrderCloseMsg msg) {
        ops.log("MQ", "[DLQ 重驱动] 关单消息 order=" + msg.orderId() + " 重试");
        try {
            orders.closeById(msg.orderId(), "支付超时，订单自动关闭（DLQ 重试）",
                    com.orangemall.order.domain.OrderStatus.CLOSED);
            log.info("dlq close redriven: {}", msg.orderId());
        } catch (Exception e) {
            ops.log("MQ", "[DLQ 重驱动失败] order=" + msg.orderId() + "：" + e.getMessage());
        }
    }

    @RabbitListener(queues = Const.MQ_PAY_DLQ, containerFactory = "listenerFactory")
    public void onPayDlq(PaySuccessMsg msg) {
        ops.log("PAY", "[DLQ 重驱动] 支付成功消息 order=" + msg.orderId() + " 重试");
        try {
            orders.onPaySuccess(msg);
            log.info("dlq pay redriven: {} -> {}", msg.paymentNo(), msg.orderId());
        } catch (Exception e) {
            ops.log("PAY", "[DLQ 重驱动失败] order=" + msg.orderId() + "：" + e.getMessage());
        }
    }
}
