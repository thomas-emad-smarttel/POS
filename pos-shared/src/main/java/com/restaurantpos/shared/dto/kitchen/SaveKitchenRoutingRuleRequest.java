package com.restaurantpos.shared.dto.kitchen;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SaveKitchenRoutingRuleRequest(
        @NotNull UUID kitchenStationId,
        UUID productId,
        UUID categoryId,
        @Min(1) @Max(1000) int precedence,
        @Min(1) @Max(240) Integer expectedPrepMinutesOverride,
        boolean active
) {
}
