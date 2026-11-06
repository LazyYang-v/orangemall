package com.orangemall.pay.service;

import com.orangemall.common.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 沙箱渠道签名（真实环境：支付宝 RSA2 / 微信 V3 平台证书）。
 * 支付回调：HMAC(paymentNo|amount|tradeStatus)；退款回调：HMAC(paymentNo|refundId|amount|tradeStatus)。
 */
@Component
public class SandboxSigner {
    @Value("${mall.pay.sandbox-key:orange-mall-sandbox-key-2026}")
    private String sandboxKey;

    public String pay(String paymentNo, int amount, String tradeStatus) {
        return JwtUtil.hmacHex(paymentNo + "|" + amount + "|" + tradeStatus, sandboxKey);
    }

    public String refund(String paymentNo, Long refundId, int amount, String tradeStatus) {
        return JwtUtil.hmacHex(paymentNo + "|" + refundId + "|" + amount + "|" + tradeStatus, sandboxKey);
    }
}
