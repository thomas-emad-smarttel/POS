package com.restaurantpos.shared.dto.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryBalanceDto(
        UUID id,
        UUID branchId,
        UUID stockLocationId,
        String locationCode,
        String locationName,
        UUID inventoryItemId,
        String itemSku,
        String itemName,
        String unitCode,
        BigDecimal onHandQuantity,
        BigDecimal reservedQuantity,
        BigDecimal availableQuantity,
        BigDecimal reorderLevel,
        BigDecimal averageUnitCost,
        BigDecimal valuationAmount,
        boolean lowStock,
        Instant lastMovementAt,
        long version
) {
}
