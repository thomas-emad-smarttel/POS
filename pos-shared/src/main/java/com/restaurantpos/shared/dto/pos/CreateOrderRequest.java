package com.restaurantpos.shared.dto.pos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateOrderRequest(
        @NotBlank @Pattern(regexp = "^(DINE_IN|TAKEAWAY|DELIVERY|COUNTER)$") String orderType,
        UUID diningTableId,
        Long expectedTableVersion,
        UUID assignedWaiterId,
        @Min(1) int guestCount,
        @Size(max = 200) String customerReference,
        @Size(max = 1000) String notes
) {
}
