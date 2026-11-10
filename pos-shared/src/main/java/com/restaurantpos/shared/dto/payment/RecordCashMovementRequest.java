package com.restaurantpos.shared.dto.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RecordCashMovementRequest(
        @NotBlank @Pattern(regexp = "^(PAY_IN|PAY_OUT|WITHDRAWAL|DEPOSIT|SAFE_DROP)$") String movementType,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Size(max = 300) String reason,
        String idempotencyKey
) {
}
