package com.restaurantpos.shared.dto.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockCountDto(
        UUID id,
        UUID branchId,
        String countNumber,
        UUID stockLocationId,
        String locationName,
        UUID categoryId,
        String status,
        UUID createdByUserId,
        UUID submittedByUserId,
        UUID approvedByUserId,
        UUID postedByUserId,
        BigDecimal totalVarianceCost,
        String notes,
        Instant submittedAt,
        Instant approvedAt,
        Instant postedAt,
        long version,
        List<StockCountItemDto> items
) {
    public record StockCountItemDto(
            UUID id,
            UUID inventoryItemId,
            String itemSku,
            String itemName,
            String unitCode,
            BigDecimal systemQuantity,
            BigDecimal countedQuantity,
            BigDecimal varianceQuantity,
            BigDecimal unitCost,
            BigDecimal varianceCost,
            boolean recountRequired,
            BigDecimal originalCountedQuantity,
            String notes,
            long version
    ) {
    }
}
