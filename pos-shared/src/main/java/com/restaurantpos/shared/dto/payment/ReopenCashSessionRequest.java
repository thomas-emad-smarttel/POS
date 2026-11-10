package com.restaurantpos.shared.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReopenCashSessionRequest(
        @NotBlank @Size(max = 300) String reason,
        long expectedVersion
) {
}
