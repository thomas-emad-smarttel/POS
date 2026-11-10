package com.restaurantpos.shared.dto.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UpdateStockCountItemsRequest(
        long expectedVersion,
        @NotEmpty @Valid List<CountLineInput> items
) {
    public record CountLineInput(
            @NotNull UUID inventoryItemId,
            @NotNull @DecimalMin("0.0000") BigDecimal countedQuantity,
            boolean recountRequired,
            @Size(max = 300) String notes
    ) {
    }
}
