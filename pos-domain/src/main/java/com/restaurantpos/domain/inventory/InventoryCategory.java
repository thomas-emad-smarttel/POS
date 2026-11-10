package com.restaurantpos.domain.inventory;

import com.restaurantpos.shared.model.TenantId;

import java.util.UUID;

public record InventoryCategory(
        UUID id,
        TenantId tenantId,
        String code,
        String name,
        String description,
        int displayOrder,
        boolean active,
        long version
) {
}
