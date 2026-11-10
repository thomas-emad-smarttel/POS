package com.restaurantpos.infrastructure.audit;

import java.util.regex.Pattern;

/**
 * Redacts sensitive credential or payment card attributes before persisting audit context JSON.
 */
public final class AuditPayloadSanitizer {

    private static final Pattern SENSITIVE_KEY_PATTERN = Pattern.compile(
            "(?i)\"(password|passwordHash|pin|pinHash|secret|token|pan|cardNumber|cvv|cvc)\"\\s*:\\s*\"[^\"]*\""
    );

    private AuditPayloadSanitizer() {
    }

    public static String sanitizeJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        return SENSITIVE_KEY_PATTERN.matcher(rawJson).replaceAll("\"$1\":\"[REDACTED]\"");
    }
}
