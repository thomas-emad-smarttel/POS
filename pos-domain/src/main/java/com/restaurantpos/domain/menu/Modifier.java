package com.restaurantpos.domain.menu;

import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record Modifier(
        UUID id,
        TenantId tenantId,
        UUID modifierGroupId,
        String code,
        String name,
        BigDecimal priceDelta,
        int displayOrder,
        boolean defaultSelected,
        boolean active,
        long version
) {
    public Modifier {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(modifierGroupId);
        Objects.requireNonNull(code);
        Objects.requireNonNull(name);
        Objects.requireNonNull(priceDelta);
    }
}
