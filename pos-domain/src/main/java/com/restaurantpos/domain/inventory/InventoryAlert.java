package com.restaurantpos.domain.inventory;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryAlert(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID inventoryItemId,
        UUID stockLocationId,
        String alertType,
        String severity,
        String message,
        BigDecimal currentValue,
        BigDecimal thresholdValue,
        String status,
        String dedupKey,
        UUID resolvedByUserId,
        Instant resolvedAt,
        Instant createdAt,
        long version
) {
}
