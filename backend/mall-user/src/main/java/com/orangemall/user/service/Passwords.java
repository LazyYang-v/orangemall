package com.orangemall.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/** 密码摘要：sha256Hex(salt + ":" + password)，与演示种子（deploy/mysql/gen-seed.ts → V2__seed.sql）生成方式一致 */
public final class Passwords {
    private static final SecureRandom RND = new SecureRandom();

    private Passwords() {
    }

    public static String newSalt() {
        byte[] b = new byte[8];
        RND.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    public static String hash(String salt, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest((salt + ":" + password).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean matches(String salt, String password, String expectHash) {
        if (password == null || expectHash == null) return false;
        return MessageDigest.isEqual(hash(salt, password).getBytes(StandardCharsets.US_ASCII),
                expectHash.getBytes(StandardCharsets.US_ASCII));
    }
}
