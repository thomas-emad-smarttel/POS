package com.restaurantpos.shared.dto.pos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AddOrderItemRequest(
        @NotNull UUID productId,
        UUID productVariantId,
        @NotNull @DecimalMin("0.0001") BigDecimal quantity,
        @Size(max = 500) String kitchenNotes,
        @Valid List<SelectedModifierInput> modifiers,
        long expectedOrderVersion
) {
    public record SelectedModifierInput(
            @NotNull UUID modifierId,
            @Min(1) int quantity
    ) {
    }
}
