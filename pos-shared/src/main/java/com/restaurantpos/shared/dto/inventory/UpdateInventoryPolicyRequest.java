package com.restaurantpos.shared.dto.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record UpdateInventoryPolicyRequest(
        @NotBlank @Pattern(regexp = "^(RESERVE_ON_SEND_CONSUME_ON_PREP|RESERVE_ON_CONFIRM_CONSUME_ON_START|DIRECT_CONSUME_ON_SEND|DISABLED)$")
        String commitmentMode,
        boolean allowNegativeStock,
        boolean lowStockAlertEnabled,
        @Min(1) @Max(365) int expiryWarningDays,
        @NotNull @DecimalMin("0.0000") BigDecimal highWasteThresholdQty,
        @NotNull @DecimalMin("0.0000") BigDecimal wasteApprovalThresholdAmount,
        @NotNull @DecimalMin("0.0000") BigDecimal countVarianceApprovalPct,
        boolean transferApprovalRequired,
        boolean allowManualConsumptionReverse
) {
}
