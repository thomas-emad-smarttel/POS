package com.restaurantpos.application.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Cryptographic helper for unpredictable opaque bearer session tokens and SHA-256 storage digests.
 */
public final class TokenSupport {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String TOKEN_PREFIX = "pos_sess_";
    private static final int TOKEN_BYTE_LENGTH = 32;

    private TokenSupport() {
    }

    public static String generateOpaqueToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String rawToken) {
        Objects.requireNonNull(rawToken, "rawToken must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
        }
    }
}
