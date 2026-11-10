package com.restaurantpos.domain.inventory;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockWasteRecord(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID stockLocationId,
        UUID inventoryItemId,
        UUID unitId,
        BigDecimal originalQuantity,
        BigDecimal baseQuantity,
        BigDecimal unitCost,
        BigDecimal totalCost,
        String reasonCode,
        String notes,
        String attachmentRef,
        UUID actorUserId,
        UUID supervisorApprovalId,
        String idempotencyKey,
        UUID movementId,
        Instant occurredAt
) {
}
