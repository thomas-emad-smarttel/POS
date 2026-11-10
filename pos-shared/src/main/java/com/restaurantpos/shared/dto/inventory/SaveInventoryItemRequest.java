package com.restaurantpos.shared.dto.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record SaveInventoryItemRequest(
        UUID categoryId,
        UUID defaultLocationId,
        boolean branchScoped,
        @NotBlank @Size(max = 80) String sku,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 1000) String description,
        @Pattern(regexp = "^(RAW_INGREDIENT|PREPARED_INGREDIENT|SEMI_FINISHED|FINISHED_GOOD|PACKAGING|CONSUMABLE|OTHER)$") String itemType,
        @Size(max = 100) String barcode,
        @NotNull UUID unitId,
        @NotNull @DecimalMin("0.0000") BigDecimal minimumStockLevel,
        @NotNull @DecimalMin("0.0000") BigDecimal reorderLevel,
        @DecimalMin("0.0000") BigDecimal initialAverageCost,
        @Pattern(regexp = "^(STANDARD|BATCH_EXPIRY|NONE)$") String trackingPolicy,
        boolean expiryTracking,
        boolean batchTracking,
        @Pattern(regexp = "^(INHERIT|DENY|ALLOW|SUPERVISOR_OVERRIDE)$") String negativeStockPolicy,
        @Pattern(regexp = "^(WEIGHTED_AVERAGE)$") String costingMethod,
        boolean active
) {
}
