package com.restaurantpos.shared.dto.inventory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateStockCountRequest(
        @NotNull UUID stockLocationId,
        UUID categoryId,
        List<UUID> inventoryItemIds,
        @Size(max = 500) String notes
) {
}
