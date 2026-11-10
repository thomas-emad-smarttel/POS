package com.restaurantpos.shared.dto.pos;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TransferOrderRequest(
        @NotNull UUID targetTableId,
        long expectedOrderVersion,
        long expectedTargetTableVersion
) {
}
