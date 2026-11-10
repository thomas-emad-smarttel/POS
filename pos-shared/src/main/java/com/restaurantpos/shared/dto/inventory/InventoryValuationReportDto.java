package com.restaurantpos.shared.dto.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryValuationReportDto(
        UUID branchId,
        Instant generatedAt,
        String costingMethod,
        BigDecimal totalOnHandValuation,
        BigDecimal totalReservedValuation,
        BigDecimal totalWasteCostRecent,
        int totalSkuCount,
        int lowStockCount,
        int outOfStockCount,
        List<ValuationLine> items
) {
    public record ValuationLine(
            UUID inventoryItemId,
            String sku,
            String name,
            String unitCode,
            BigDecimal totalOnHand,
            BigDecimal totalReserved,
            BigDecimal totalAvailable,
            BigDecimal reorderLevel,
            BigDecimal averageUnitCost,
            BigDecimal totalValue,
            boolean lowStock
    ) {
    }
}
