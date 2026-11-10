package com.restaurantpos.shared.dto.pos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SaveProductRequest(
        @NotNull UUID categoryId,
        UUID taxId,
        @NotBlank @Size(max = 80) String sku,
        @Size(max = 100) String barcode,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 1000) String description,
        @NotNull @DecimalMin("0.0000") BigDecimal basePrice,
        @NotBlank @Pattern(regexp = "^(FIXED|WEIGHTED|OPEN)$") String pricingMode,
        @NotBlank @Pattern(regexp = "^(STANDARD|COMBO|MODIFIER_ONLY|SERVICE)$") String productType,
        @Size(max = 500) String imageRef,
        int displayOrder,
        boolean active,
        List<UUID> modifierGroupIds
) {
}
