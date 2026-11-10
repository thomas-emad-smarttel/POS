package com.restaurantpos.shared.dto.inventory;

import java.math.BigDecimal;
import java.util.UUID;

public record UnitConversionDto(
        UUID id,
        UUID inventoryItemId,
        String itemSku,
        UUID fromUnitId,
        String fromUnitCode,
        UUID toUnitId,
        String toUnitCode,
        BigDecimal conversionFactor,
        boolean active
) {
}
