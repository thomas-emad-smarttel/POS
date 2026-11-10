package com.restaurantpos.shared.dto.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record RecordStockAdjustmentRequest(
        @NotNull UUID stockLocationId,
        @NotNull UUID inventoryItemId,
        @NotNull UUID unitId,
        @NotNull @DecimalMin("0.0001") BigDecimal quantity,
        @NotBlank @Pattern(regexp = "^(OPENING_BALANCE|STOCK_ADJUSTMENT_IN|STOCK_ADJUSTMENT_OUT|DAMAGE|CUSTOMER_RETURN|PRODUCTION_OUTPUT|PRODUCTION_CONSUMPTION|REVERSAL)$")
        String adjustmentType,
        @DecimalMin("0.0000") BigDecimal unitCost,
        @NotBlank @Size(max = 300) String reason,
        @Size(max = 80) String batchNumber,
        @Size(max = 32) String expiryDate,
        @NotBlank @Size(max = 128) String idempotencyKey,
        UUID supervisorApprovalId,
        boolean allowNegativeOverride
) {
}
