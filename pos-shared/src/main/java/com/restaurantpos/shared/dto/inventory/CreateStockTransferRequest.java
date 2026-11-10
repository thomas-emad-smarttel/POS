package com.restaurantpos.shared.dto.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateStockTransferRequest(
        @NotNull UUID sourceLocationId,
        @NotNull UUID destinationBranchId,
        @NotNull UUID destinationLocationId,
        @Size(max = 500) String notes,
        @NotEmpty @Valid List<TransferLineInput> items
) {
    public record TransferLineInput(
            @NotNull UUID inventoryItemId,
            @NotNull UUID unitId,
            @NotNull @DecimalMin("0.0001") BigDecimal quantity,
            @Size(max = 300) String notes
    ) {
    }
}
