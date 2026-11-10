package com.restaurantpos.shared.dto.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockReservationDto(
        UUID id,
        UUID branchId,
        UUID orderId,
        UUID sourceEventId,
        String status,
        Instant createdAt,
        Instant updatedAt,
        long version,
        List<StockReservationItemDto> items
) {
    public record StockReservationItemDto(
            UUID id,
            UUID orderItemId,
            UUID kitchenTicketItemId,
            UUID inventoryItemId,
            String itemSku,
            String itemName,
            UUID stockLocationId,
            UUID recipeVersionId,
            BigDecimal reservedQuantity,
            BigDecimal consumedQuantity,
            BigDecimal releasedQuantity,
            String status,
            long version
    ) {
    }
}
