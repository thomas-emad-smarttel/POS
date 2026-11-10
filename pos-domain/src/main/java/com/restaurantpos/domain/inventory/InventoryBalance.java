package com.restaurantpos.domain.inventory;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Authoritative projection tracking OnHand, Reserved, Weighted Average Cost, and derived Available stock.
 */
public record InventoryBalance(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID stockLocationId,
        UUID inventoryItemId,
        BigDecimal onHandQuantity,
        BigDecimal reservedQuantity,
        BigDecimal averageUnitCost,
        Instant lastMovementAt,
        Instant lastCostUpdatedAt,
        long version
) {
    public BigDecimal availableQuantity() {
        BigDecimal oh = onHandQuantity != null ? onHandQuantity : BigDecimal.ZERO;
        BigDecimal res = reservedQuantity != null ? reservedQuantity : BigDecimal.ZERO;
        return oh.subtract(res);
    }
}
