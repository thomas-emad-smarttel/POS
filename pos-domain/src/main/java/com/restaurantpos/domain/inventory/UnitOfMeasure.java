package com.restaurantpos.domain.inventory;

import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

public record UnitOfMeasure(
        UUID id,
        TenantId tenantId,
        String code,
        String name,
        String unitType,
        UUID baseUnitId,
        BigDecimal conversionFactor,
        long version
) {
}
