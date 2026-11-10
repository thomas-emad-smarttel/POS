package com.restaurantpos.shared.dto.pos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record UpdateOrderItemRequest(
        @NotNull @DecimalMin("0.0001") BigDecimal quantity,
        @Size(max = 500) String kitchenNotes,
        @Valid List<AddOrderItemRequest.SelectedModifierInput> modifiers,
        long expectedOrderVersion
) {
}
