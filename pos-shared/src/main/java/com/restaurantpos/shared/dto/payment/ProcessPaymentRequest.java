package com.restaurantpos.shared.dto.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ProcessPaymentRequest(
        @NotNull UUID orderId,
        @NotNull UUID paymentMethodId,
        @NotBlank @Size(max = 128) String idempotencyKey,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @DecimalMin("0.00") BigDecimal tenderedAmount,
        @DecimalMin("0.00") BigDecimal tipAmount,
        UUID cashSessionId,
        @Size(max = 64) String terminalRef,
        @Size(max = 64) String simulatedOutcome
) {
}
