package com.restaurantpos.shared.dto.inventory;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record StockCountActionRequest(
        long expectedVersion,
        UUID supervisorApprovalId,
        @Size(max = 300) String notes
) {
}
