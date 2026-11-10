package com.restaurantpos.shared.dto.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockWasteRecordDto(
        UUID id,
        UUID branchId,
        UUID stockLocationId,
        UUID inventoryItemId,
        String itemSku,
        String itemName,
        UUID unitId,
        String unitCode,
        BigDecimal originalQuantity,
        BigDecimal baseQuantity,
        BigDecimal unitCost,
        BigDecimal totalCost,
        String reasonCode,
        String notes,
        String attachmentRef,
        UUID actorUserId,
        UUID supervisorApprovalId,
        UUID movementId,
        Instant occurredAt
) {
}
