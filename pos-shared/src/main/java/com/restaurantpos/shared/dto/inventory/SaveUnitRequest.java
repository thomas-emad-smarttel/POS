package com.restaurantpos.shared.dto.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record SaveUnitRequest(
        @NotBlank @Size(max = 32) String code,
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Pattern(regexp = "^(WEIGHT|VOLUME|COUNT)$") String unitType,
        UUID baseUnitId,
        @DecimalMin("0.000001") BigDecimal conversionFactor
) {
}
