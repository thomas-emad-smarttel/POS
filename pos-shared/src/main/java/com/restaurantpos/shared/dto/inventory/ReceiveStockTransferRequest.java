package com.restaurantpos.shared.dto.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ReceiveStockTransferRequest(
        long expectedVersion,
        @NotEmpty @Valid List<ReceiveLineInput> lines,
        @Size(max = 300) String notes
) {
    public record ReceiveLineInput(
            @NotNull UUID transferItemId,
            @NotNull @DecimalMin("0.0000") BigDecimal receivedQuantity,
            @NotNull @DecimalMin("0.0000") BigDecimal damagedQuantity,
            @NotNull @DecimalMin("0.0000") BigDecimal missingQuantity,
            @Size(max = 300) String notes
    ) {
    }
}
