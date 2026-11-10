package com.restaurantpos.shared.dto.kitchen;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateKitchenItemStatusRequest(
        @NotBlank @Pattern(regexp = "^(QUEUED|PREPARING|READY|SERVED|CANCELLED)$") String status,
        long expectedVersion,
        @Size(max = 300) String reason
) {
}
