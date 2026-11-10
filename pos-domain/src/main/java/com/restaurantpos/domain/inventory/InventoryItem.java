package com.restaurantpos.domain.inventory;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryItem(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID categoryId,
        UUID defaultLocationId,
        String sku,
        String name,
        String description,
        String itemType,
        String barcode,
        UUID unitId,
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
