package com.restaurantpos.shared.dto.pos;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateTaxRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 120) String name,
        @NotNull @DecimalMin("0.0000") @DecimalMax("100.0000") BigDecimal ratePercent,
        boolean inclusive,
        boolean active
) {
}
