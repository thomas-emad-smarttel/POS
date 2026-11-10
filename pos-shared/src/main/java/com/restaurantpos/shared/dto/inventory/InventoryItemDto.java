package com.restaurantpos.shared.dto.inventory;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryItemDto(
        UUID id,
        UUID branchId,
        UUID categoryId,
        String categoryName,
        UUID defaultLocationId,
        String sku,
        String name,
        String description,
        String itemType,
        String barcode,
        UUID unitId,
        String unitCode,
        BigDecimal minimumStockLevel,
        BigDecimal reorderLevel,
        BigDecimal averageCost,
        String trackingPolicy,
        boolean expiryTracking,
        boolean batchTracking,
        String negativeStockPolicy,
        String costingMethod,
        boolean active,
        long version
) {
}
