package com.restaurantpos.shared.dto.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundRequest(
        @NotNull UUID orderId,
        @NotNull UUID originalPaymentId,
        @NotBlank @Size(max = 128) String idempotencyKey,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Size(max = 300) String reason,
        UUID supervisorApprovalId,
        UUID cashSessionId,
        @Size(max = 64) String simulatedOutcome
) {
}
