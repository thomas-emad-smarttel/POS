package com.restaurantpos.shared.dto.kitchen;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ResolveUnroutedItemRequest(
        @NotNull UUID kitchenStationId,
        boolean saveCategoryOrProductRule
) {
}
