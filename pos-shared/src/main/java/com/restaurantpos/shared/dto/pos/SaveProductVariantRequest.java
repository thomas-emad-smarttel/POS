package com.restaurantpos.shared.dto.pos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SaveProductVariantRequest(
        @NotBlank @Size(max = 80) String sku,
        @Size(max = 100) String barcode,
        @NotBlank @Size(max = 150) String name,
        @NotNull @DecimalMin("0.0000") BigDecimal priceOverride,
        int displayOrder,
        boolean active
) {
}
