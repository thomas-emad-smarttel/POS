package com.restaurantpos.shared.dto.pos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SaveModifierRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 150) String name,
        @NotNull @DecimalMin("0.0000") BigDecimal priceDelta,
        int displayOrder,
        boolean defaultSelected,
        boolean active
) {
}
