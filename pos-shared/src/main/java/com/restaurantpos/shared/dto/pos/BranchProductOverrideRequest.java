package com.restaurantpos.shared.dto.pos;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.util.UUID;

public record BranchProductOverrideRequest(
        @DecimalMin("0.0000") BigDecimal priceOverride,
        UUID taxOverrideId,
        boolean available
) {
}
