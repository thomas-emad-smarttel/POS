package com.restaurantpos.domain.inventory;

import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

public record StockTransferItem(
        UUID id,
        TenantId tenantId,
        UUID stockTransferId,
        UUID inventoryItemId,
        UUID unitId,
        BigDecimal requestedQuantity,
        BigDecimal dispatchedQuantity,
        BigDecimal receivedQuantity,
        BigDecimal damagedQuantity,
        BigDecimal missingQuantity,
        BigDecimal unitCost,
        String notes,
        long version
) {
}
