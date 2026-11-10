package com.restaurantpos.shared.dto.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CloseCashSessionRequest(
        @NotNull @DecimalMin("0.00") BigDecimal countedCashAmount,
        long expectedVersion
) {
}
