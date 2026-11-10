package com.restaurantpos.domain.inventory;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockMovement(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID stockLocationId,
        UUID inventoryItemId,
        UUID actorUserId,
        StockMovementType movementType,
        String direction,
        BigDecimal quantityDelta,
        UUID unitId,
        BigDecimal originalQuantity,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        BigDecimal reservedBefore,
        BigDecimal reservedAfter,
        BigDecimal unitCost,
        BigDecimal totalCost,
        String referenceType,
        UUID referenceId,
        String reason,
        String batchNumber,
        String expiryDate,
        UUID approvalId,
        UUID outboxEventId,
        String idempotencyKey,
        Instant occurredAt
) {
}
