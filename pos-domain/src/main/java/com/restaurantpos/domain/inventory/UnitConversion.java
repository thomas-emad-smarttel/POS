package com.restaurantpos.domain.inventory;

import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

public record UnitConversion(
        UUID id,
        TenantId tenantId,
        UUID inventoryItemId,
        UUID fromUnitId,
        UUID toUnitId,
        BigDecimal conversionFactor,
        boolean active,
        long version
) {
}
