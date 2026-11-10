package com.restaurantpos.domain.menu;

import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record TaxDefinition(
        UUID id,
        TenantId tenantId,
        String code,
        String name,
        BigDecimal ratePercent,
        boolean inclusive,
        boolean active,
        long version
) {
    public TaxDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(code);
        Objects.requireNonNull(name);
        Objects.requireNonNull(ratePercent);
    }
}
