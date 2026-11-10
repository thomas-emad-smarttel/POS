package com.restaurantpos.shared.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed tenant identifier for multi-tenant isolation.
 */
public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "tenantId must not be null");
    }

    public static TenantId of(UUID value) {
        return new TenantId(value);
    }

    public static TenantId fromString(String raw) {
        return new TenantId(UUID.fromString(Objects.requireNonNull(raw, "tenantId string must not be null")));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
