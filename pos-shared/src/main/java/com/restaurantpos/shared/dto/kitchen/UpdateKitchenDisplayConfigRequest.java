package com.restaurantpos.shared.dto.kitchen;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateKitchenDisplayConfigRequest(
        boolean expoEnabled,
        @NotBlank @Pattern(regexp = "^(FIFO|PRIORITY|OVERDUE_FIRST)$") String defaultSortMode,
        @Min(1) @Max(240) int defaultExpectedPrepMinutes,
        boolean autoPrintOnDispatch
) {
}
