package com.restaurantpos.shared.dto.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record SaveUnitConversionRequest(
        UUID inventoryItemId,
        @NotNull UUID fromUnitId,
        @NotNull UUID toUnitId,
        @NotNull @DecimalMin("0.000001") BigDecimal conversionFactor,
        boolean active
) {
}
