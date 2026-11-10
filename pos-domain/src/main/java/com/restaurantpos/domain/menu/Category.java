package com.restaurantpos.domain.menu;

import com.restaurantpos.shared.model.TenantId;

import java.util.Objects;
import java.util.UUID;

public record Category(
        UUID id,
        TenantId tenantId,
        UUID parentId,
        String code,
        String name,
        int displayOrder,
        String colorHex,
        boolean active,
        long version
) {
    public Category {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(code);
        Objects.requireNonNull(name);
    }
}
