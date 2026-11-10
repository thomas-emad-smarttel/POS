package com.restaurantpos.domain.tenancy;

import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain representation of a restaurant organization tenant.
 */
public record Tenant(
        UUID id,
        String code,
        String legalName,
        String displayName,
        String defaultCurrency,
        String status,
        long version
) {
    public Tenant {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(legalName, "legalName must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(defaultCurrency, "defaultCurrency must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
