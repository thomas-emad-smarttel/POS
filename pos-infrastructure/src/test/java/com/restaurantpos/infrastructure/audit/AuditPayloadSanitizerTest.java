package com.restaurantpos.infrastructure.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditPayloadSanitizerTest {

    @Test
    void redactsSecretsAndCardholderFields() {
        String raw = "{\"user\":\"cashier1\",\"password\":\"Secret123!\",\"pin\":\"1234\",\"pan\":\"4111111111111111\",\"cvv\":\"999\",\"reason\":\"shift\"}";
        String sanitized = AuditPayloadSanitizer.sanitizeJson(raw);

        assertFalse(sanitized.contains("Secret123!"));
        assertFalse(sanitized.contains("1234"));
        assertFalse(sanitized.contains("4111111111111111"));
        assertFalse(sanitized.contains("999"));
        assertTrue(sanitized.contains("\"reason\":\"shift\""));
        assertTrue(sanitized.contains("[REDACTED]"));
    }

    @Test
    void handlesNullAndBlankInputs() {
        assertNull(AuditPayloadSanitizer.sanitizeJson(null));
        assertNull(AuditPayloadSanitizer.sanitizeJson("   "));
    }
}
