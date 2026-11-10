package com.restaurantpos.shared.dto.pos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ChangeOrderTypeRequest(
        @NotBlank @Pattern(regexp = "^(DINE_IN|TAKEAWAY|DELIVERY|COUNTER)$") String orderType,
        UUID diningTableId,
        @Size(max = 200) String customerReference,
        long expectedOrderVersion
) {
}
