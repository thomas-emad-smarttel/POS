package com.restaurantpos.shared.dto.inventory;

import java.math.BigDecimal;
import java.util.UUID;

public record UnitOfMeasureDto(
        UUID id,
        String code,
        String name,
        String unitType,
        UUID baseUnitId,
        BigDecimal conversionFactor,
        long version
) {
}
