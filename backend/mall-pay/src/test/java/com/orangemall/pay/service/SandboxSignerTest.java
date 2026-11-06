package com.orangemall.pay.service;

import com.orangemall.common.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** 沙箱渠道验签：回调的任一字段被篡改（金额、状态、流水号、退款单）或密钥不同，签名都必须不同 */
class SandboxSignerTest {
    SandboxSigner signer = new SandboxSigner();

    @BeforeEach
    void key() {
        ReflectionTestUtils.setField(signer, "sandboxKey", "unit-test-key");
    }

    @Test
    void paySignatureIsHmacOverPipeJoinedFields() {
        assertThat(signer.pay("P1001", 9900, "TRADE_SUCCESS"))
                .isEqualTo(JwtUtil.hmacHex("P1001|9900|TRADE_SUCCESS", "unit-test-key"))
                .hasSize(64)
                .isEqualTo(signer.pay("P1001", 9900, "TRADE_SUCCESS"));
    }

    @Test
    void tamperingAnyFieldChangesPaySignature() {
        String base = signer.pay("P1001", 9900, "TRADE_SUCCESS");
        assertThat(signer.pay("P1001", 1, "TRADE_SUCCESS")).isNotEqualTo(base);       // 改金额
        assertThat(signer.pay("P1001", 9900, "TRADE_CLOSED")).isNotEqualTo(base);     // 改状态
        assertThat(signer.pay("P1002", 9900, "TRADE_SUCCESS")).isNotEqualTo(base);    // 改流水号
        SandboxSigner other = new SandboxSigner();
        ReflectionTestUtils.setField(other, "sandboxKey", "another-key");
        assertThat(other.pay("P1001", 9900, "TRADE_SUCCESS")).isNotEqualTo(base);     // 换密钥
    }

    @Test
    void refundSignatureBindsRefundId() {
        String a = signer.refund("P1001", 11L, 9900, "REFUND_SUCCESS");
        assertThat(a).isEqualTo(JwtUtil.hmacHex("P1001|11|9900|REFUND_SUCCESS", "unit-test-key"));
        assertThat(signer.refund("P1001", 12L, 9900, "REFUND_SUCCESS")).isNotEqualTo(a);
        // 支付签名不能被当作退款签名复用
        assertThat(signer.pay("P1001", 9900, "REFUND_SUCCESS")).isNotEqualTo(a);
    }
}
