package com.restaurantpos.shared.dto.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockTransferDto(
        UUID id,
        String transferNumber,
        UUID sourceBranchId,
        UUID sourceLocationId,
        String sourceLocationName,
        UUID destinationBranchId,
        UUID destinationLocationId,
        String destinationLocationName,
        String status,
        UUID requestedByUserId,
        UUID approvedByUserId,
        UUID dispatchedByUserId,
        UUID receivedByUserId,
        String notes,
        Instant requestedAt,
        Instant approvedAt,
        Instant dispatchedAt,
        Instant receivedAt,
        long version,
        List<StockTransferItemDto> items
) {
    public record StockTransferItemDto(
            UUID id,
            UUID inventoryItemId,
            String itemSku,
            String itemName,
            UUID unitId,
            String unitCode,
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
}
