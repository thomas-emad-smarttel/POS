package com.restaurantpos.shared.dto.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockMovementDto(
        UUID id,
        UUID branchId,
        UUID stockLocationId,
        String locationCode,
        UUID inventoryItemId,
        String itemSku,
        String itemName,
        UUID actorUserId,
        String movementType,
        String direction,
        BigDecimal quantityDelta,
        UUID inputUnitId,
        String inputUnitCode,
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
        String idempotencyKey,
        Instant occurredAt
) {
}
