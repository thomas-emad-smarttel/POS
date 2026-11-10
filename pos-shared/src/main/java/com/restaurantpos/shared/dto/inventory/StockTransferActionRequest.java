package com.restaurantpos.shared.dto.inventory;

import jakarta.validation.constraints.Size;

public record StockTransferActionRequest(
        long expectedVersion,
        @Size(max = 300) String notes
) {
}
