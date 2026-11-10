package com.restaurantpos.domain.inventory;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Optimistically locked stock balance projection per location and ingredient.
 */
public record StockBalanceView(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID stockLocationId,
        UUID ingredientId,
        BigDecimal quantityOnHand,
        long version
) {
}
