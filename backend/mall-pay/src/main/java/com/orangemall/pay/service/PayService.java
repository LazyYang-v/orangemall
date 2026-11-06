package com.orangemall.pay.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.orangemall.api.client.OrderInnerClient;
import com.orangemall.api.dto.OrderBrief;
import com.orangemall.api.dto.PaySuccessMsg;
import com.orangemall.common.BizException;
import com.orangemall.common.Const;
import com.orangemall.common.IdGenerator;
import com.orangemall.pay.entity.Payment;
import com.orangemall.pay.entity.PayNotifyLog;
import com.orangemall.pay.mapper.NotifyLogMapper;
import com.orangemall.pay.mapper.PayMapper;
import com.orangemall.web.OpsLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付：沙箱模拟支付宝/微信收单。真实环境验签是 RSA2 / V3 平台证书，此处用 HMAC-SHA256 模拟（密钥不可得，见设计文档）。
 * 回调顺序：验签 → 幂等（status != PENDING 直接 success）→ 金额校验 → CAS 更新 → MQ 通知订单服务。
 */
@Service
@RequiredArgsConstructor
public class PayService {
    private final PayMapper payMapper;
    private final NotifyLogMapper notifyLogMapper;
    private final OpsLogger ops;
    private final OrderInnerClient orders;
    private final RabbitTemplate rabbit;
    private final SandboxSigner signer;
    private final RefundService refund;

    public Map<String, Object> create(long userId, Long orderId, String channel) {
        OrderBrief brief = orders.brief(orderId).unwrap();
        if (!"PENDING_PAYMENT".equals(brief.status())) {
            throw new BizException(6001, "CLOSED".equals(brief.status()) ? "订单已超时关闭" : "订单状态不可支付");
        }
        // 越权防护：只能为本人订单创建支付流水（不区分存在/非本人，统一 6002 防枚举）
        if (!brief.userId().equals(userId)) throw new BizException(6002, "支付流水不存在");
        // 同一订单同一渠道复用未完成流水
        Payment p = payMapper.selectList(new LambdaUpdateWrapper<Payment>()
                .eq(Payment::getOrderId, orderId).eq(Payment::getChannel, channel).eq(Payment::getStatus, "PENDING")
                .last("LIMIT 1")).stream().findFirst().orElse(null);
        if (p == null) {
            p = new Payment();
            p.setId(IdGenerator.nextId());
            p.setPaymentNo("P" + p.getId());
            p.setOrderId(orderId);
            p.setUserId(userId);
            p.setChannel(channel);
            p.setAmount(brief.payAmount());
            p.setStatus("PENDING");
            p.setQrCode(("ALIPAY".equals(channel) ? "https://qr.alipay-sandbox.com/" : "weixin://wxpay/bizpayurl?pr=") + p.getPaymentNo());
            p.setExpireAt(brief.payDeadline());
            p.setCallbackCount(0);
            Instant now = Instant.now();
            p.setCreatedAt(now);
            p.setUpdatedAt(now);
            payMapper.insert(p);
        }
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("paymentNo", p.getPaymentNo());
        vo.put("orderId", String.valueOf(p.getOrderId()));
        vo.put("channel", p.getChannel());
        vo.put("amount", p.getAmount());
        vo.put("status", p.getStatus());
        vo.put("qrCode", p.getQrCode());
        vo.put("expireAt", p.getExpireAt());
        return vo;
    }

    public Map<String, Object> status(long userId, String paymentNo) {
        Payment p = findOwned(userId, paymentNo);
        return Map.of("status", p.getStatus(), "orderId", String.valueOf(p.getOrderId()), "callbackCount", p.getCallbackCount());
    }

    /** 沙箱「扫码支付」：生成带签名的回调报文（真实环境由支付宝/微信服务器发起） */
    public Map<String, Object> sandboxPay(long userId, String paymentNo, boolean success) {
        Payment p = findOwned(userId, paymentNo);
        String tradeStatus = success ? "TRADE_SUCCESS" : "TRADE_CLOSED";
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("paymentNo", p.getPaymentNo());
        out.put("amount", p.getAmount());
        out.put("tradeStatus", tradeStatus);
        out.put("sign", signer.pay(p.getPaymentNo(), p.getAmount(), tradeStatus));
        return out;
    }

    public Map<String, Object> notify(String paymentNo, int amount, String tradeStatus, String sign) {
        notify(paymentNo, null, amount, tradeStatus, sign);
        return Map.of("result", "success");
    }

    /** 统一回调入口：tradeStatus=REFUND_SUCCESS 走退款分支（docs/refund-design.md §3） */
    public Map<String, Object> notify(String paymentNo, Long refundId, int amount, String tradeStatus, String sign) {
        String pn = paymentNo;
        if (tradeStatus.equals("REFUND_SUCCESS")) {
            refund.handleRefundNotify(pn, refundId, amount, sign);
            audit(pn, tradeStatus, amount, true, "REFUND_SUCCESS");
            return Map.of("result", "success", "channelRefund", true);
        }
        if (!signer.pay(pn, amount, tradeStatus).equals(sign)) {
            ops.log("PAY", "[回调] " + pn + " 验签失败，拒绝处理");
            audit(pn, tradeStatus, amount, false, "BAD_SIGN");
            throw new BizException(6003, "验签失败");
        }
        Payment p = find(pn);
        int count = p.getCallbackCount() + 1;
        payMapper.update(null, new LambdaUpdateWrapper<Payment>()
                .eq(Payment::getPaymentNo, pn).set(Payment::getCallbackCount, count).set(Payment::getUpdatedAt, Instant.now()));
        p.setCallbackCount(count);
        if (!"PENDING".equals(p.getStatus())) {
            ops.log("PAY", "[回调] " + pn + " 第 " + count + " 次回调，状态已为 " + p.getStatus() + "，幂等忽略");
            audit(pn, tradeStatus, amount, true, "IDEMPOTENT");
            return Map.of("result", "success", "idempotent", true, "callbackCount", count);
        }
        if (amount != p.getAmount()) {
            audit(pn, tradeStatus, amount, true, "AMOUNT_MISMATCH");
            throw new BizException(6004, "金额不一致");
        }
        OrderBrief brief = orders.brief(p.getOrderId()).unwrap();
        if ("TRADE_SUCCESS".equals(tradeStatus)) {
            if (!"PENDING_PAYMENT".equals(brief.status())) {
                // 已关单后才到账：标记异常单，走自动退款
                payMapper.update(null, new LambdaUpdateWrapper<Payment>().eq(Payment::getPaymentNo, pn)
                        .set(Payment::getStatus, "CLOSED").set(Payment::getUpdatedAt, Instant.now()));
                ops.log("PAY", "[回调] " + pn + " 订单已关闭仍收到支付，进入自动退款流程");
                audit(pn, tradeStatus, amount, true, "REFUND");
                return Map.of("result", "success", "refund", true);
            }
            Instant paidAt = Instant.now();
            int rows = payMapper.update(null, new LambdaUpdateWrapper<Payment>().eq(Payment::getPaymentNo, pn)
                    .eq(Payment::getStatus, "PENDING")
                    .set(Payment::getStatus, "SUCCESS").set(Payment::getPaidAt, paidAt).set(Payment::getUpdatedAt, Instant.now()));
            if (rows == 0) {
                audit(pn, tradeStatus, amount, true, "IDEMPOTENT");
                return Map.of("result", "success", "idempotent", true, "callbackCount", count);
            }
            ops.log("PAY", "[回调] " + pn + " 验签通过，UPDATE ... WHERE status='PENDING' 成功，订单 " + brief.id() + " → 已支付");
            audit(pn, tradeStatus, amount, true, "SUCCESS");
            rabbit.convertAndSend(Const.MQ_PAY_EXCHANGE, Const.MQ_PAY_SUCCESS_KEY, new PaySuccessMsg(p.getOrderId(), pn, p.getChannel(), paidAt));
            return Map.of("result", "success", "idempotent", false, "callbackCount", count);
        }
        payMapper.update(null, new LambdaUpdateWrapper<Payment>().eq(Payment::getPaymentNo, pn)
                .set(Payment::getStatus, "FAILED").set(Payment::getUpdatedAt, Instant.now()));
        ops.log("PAY", "[回调] " + pn + " 支付失败，订单保持待支付可重新发起");
        audit(pn, tradeStatus, amount, true, "FAILED");
        return Map.of("result", "success", "idempotent", false, "callbackCount", count);
    }

    private Payment find(String paymentNo) {
        return payMapper.selectList(new LambdaUpdateWrapper<Payment>().eq(Payment::getPaymentNo, paymentNo).last("LIMIT 1"))
                .stream().findFirst().orElseThrow(() -> new BizException(6002, "支付流水不存在"));
    }

    /** 本人流水查询：非本人订单一律 6002（防流水号枚举探测他人支付） */
    private Payment findOwned(long userId, String paymentNo) {
        Payment p = find(paymentNo);
        if (!p.getUserId().equals(userId)) throw new BizException(6002, "支付流水不存在");
        return p;
    }

    /** 每次回调留痕 */
    private void audit(String paymentNo, String tradeStatus, int amount, boolean signOk, String result) {
        PayNotifyLog l = new PayNotifyLog();
        l.setPaymentNo(paymentNo);
        l.setTradeStatus(tradeStatus);
        l.setAmount(amount);
        l.setSignOk(signOk);
        l.setResult(result);
        Instant now = Instant.now();
        l.setCreatedAt(now);
        l.setUpdatedAt(now);
        notifyLogMapper.insert(l);
    }
}
