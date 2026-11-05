package com.orangemall.common;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/** 极简 HS256 JWT（网关 Reactive 与 Servlet 服务通用，不依赖第三方库） */
public final class JwtUtil {
    private static final ObjectMapper M = new ObjectMapper();
    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();
    private static final String HEADER = ENC.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    public record Claims(long userId, String role, String jti, long exp) {
        public long ttlSeconds() {
            return Math.max(1, exp - System.currentTimeMillis() / 1000);
        }
    }

    private JwtUtil() {
    }

    public static String create(long userId, String role, long ttlSeconds, String secret) {
        try {
            long exp = System.currentTimeMillis() / 1000 + ttlSeconds;
            String payload = ENC.encodeToString(M.writeValueAsBytes(Map.of(
                    "sub", String.valueOf(userId), "role", role, "jti", UUID.randomUUID().toString().replace("-", ""), "exp", exp)));
            String body = HEADER + "." + payload;
            return body + "." + ENC.encodeToString(hmac(body, secret));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 校验签名与过期时间，失败返回 null */
    public static Claims parse(String token, String secret) {
        try {
            String[] p = token.split("\\.");
            if (p.length != 3) return null;
            byte[] expect = hmac(p[0] + "." + p[1], secret);
            if (!MessageDigest.isEqual(expect, DEC.decode(p[2]))) return null;
            Map<?, ?> m = M.readValue(DEC.decode(p[1]), Map.class);
            long exp = ((Number) m.get("exp")).longValue();
            if (exp < System.currentTimeMillis() / 1000) return null;
            return new Claims(Long.parseLong((String) m.get("sub")), (String) m.get("role"), (String) m.get("jti"), exp);
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] hmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String hmacHex(String data, String secret) {
        StringBuilder sb = new StringBuilder();
        for (byte b : hmac(data, secret)) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
