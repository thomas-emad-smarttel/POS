package com.restaurantpos.shared.dto.pos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SaveDiningTableRequest(
        @NotNull UUID diningAreaId,
        @NotBlank @Size(max = 32) String tableCode,
        @NotBlank @Size(max = 80) String displayName,
        @Min(1) int capacity,
        int sortOrder,
        boolean active
) {
}
