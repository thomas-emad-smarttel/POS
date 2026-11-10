package com.restaurantpos.shared.dto.pos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CancelOrderRequest(
        @NotBlank @Size(max = 300) String reason,
        UUID supervisorApprovalId,
        long expectedOrderVersion
) {
}
