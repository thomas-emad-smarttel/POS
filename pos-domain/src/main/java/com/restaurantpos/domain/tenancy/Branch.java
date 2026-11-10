package com.restaurantpos.domain.tenancy;

import com.restaurantpos.shared.model.TenantId;

import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain representation of a restaurant branch.
 */
public record Branch(
        UUID id,
        TenantId tenantId,
        String code,
        String name,
        String timezone,
        String currencyCode,
        boolean active,
        long version
) {
    public Branch {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(timezone, "timezone must not be null");
        Objects.requireNonNull(currencyCode, "currencyCode must not be null");
    }
}
