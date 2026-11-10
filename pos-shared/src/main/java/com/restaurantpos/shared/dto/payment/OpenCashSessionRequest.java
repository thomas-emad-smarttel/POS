package com.restaurantpos.shared.dto.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record OpenCashSessionRequest(
        UUID terminalId,
        @NotNull @DecimalMin("0.00") BigDecimal openingFloat
) {
}
