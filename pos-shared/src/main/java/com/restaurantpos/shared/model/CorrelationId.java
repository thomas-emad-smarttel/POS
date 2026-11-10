package com.restaurantpos.shared.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Request and audit correlation identifier propagated across desktop, local server, and logs.
 */
public record CorrelationId(String value) {

    public CorrelationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (value.length() > 64) {
            throw new IllegalArgumentException("correlationId length must not exceed 64 characters");
        }
    }

    public static CorrelationId generate() {
        return new CorrelationId(UUID.randomUUID().toString());
    }

    public static CorrelationId of(String raw) {
        return new CorrelationId(Objects.requireNonNull(raw, "correlationId must not be null").trim());
    }

    @Override
    public String toString() {
        return value;
    }
}
