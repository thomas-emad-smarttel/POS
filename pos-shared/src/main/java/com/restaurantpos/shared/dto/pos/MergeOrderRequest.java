package com.restaurantpos.shared.dto.pos;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MergeOrderRequest(
        @NotNull UUID sourceOrderId,
        long expectedTargetOrderVersion,
        long expectedSourceOrderVersion
) {
}
