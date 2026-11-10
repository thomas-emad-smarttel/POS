package com.restaurantpos.shared.dto.pos;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record SplitOrderRequest(
        @NotEmpty List<UUID> orderItemIds,
        UUID targetTableId,
        long expectedOrderVersion
) {
}
