package com.restaurantpos.shared.dto.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryAlertDto(
        UUID id,
        UUID branchId,
        UUID inventoryItemId,
        String itemSku,
        String itemName,
        UUID stockLocationId,
        String alertType,
        String severity,
        String message,
        BigDecimal currentValue,
        BigDecimal thresholdValue,
        String status,
        Instant createdAt,
        Instant resolvedAt,
        long version
) {
}
