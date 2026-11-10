package com.restaurantpos.domain.inventory;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

public record StockReservationItem(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID reservationId,
        UUID orderItemId,
        UUID kitchenTicketItemId,
        UUID inventoryItemId,
        UUID stockLocationId,
        UUID recipeVersionId,
        BigDecimal reservedQuantity,
        BigDecimal consumedQuantity,
        BigDecimal releasedQuantity,
        String status,
        String idempotencyKey,
        long version
) {
}
