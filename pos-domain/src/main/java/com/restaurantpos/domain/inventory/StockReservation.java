package com.restaurantpos.domain.inventory;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockReservation(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID orderId,
        UUID sourceEventId,
        String status,
        String recipeSnapshotJson,
        Instant createdAt,
        Instant updatedAt,
        long version,
        List<StockReservationItem> items
) {
}
