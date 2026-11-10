package com.restaurantpos.domain.inventory;

import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

public record StockCountItem(
        UUID id,
        TenantId tenantId,
        UUID stockCountId,
        UUID inventoryItemId,
        BigDecimal systemQuantity,
        BigDecimal countedQuantity,
        BigDecimal varianceQuantity,
        BigDecimal unitCost,
        BigDecimal varianceCost,
        boolean recountRequired,
        BigDecimal originalCountedQuantity,
        String notes,
        long version
) {
}
