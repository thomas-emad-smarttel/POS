package com.restaurantpos.shared.dto.inventory;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryPolicyDto(
        UUID id,
        UUID branchId,
        String commitmentMode,
        boolean allowNegativeStock,
        boolean lowStockAlertEnabled,
        int expiryWarningDays,
        BigDecimal highWasteThresholdQty,
        BigDecimal wasteApprovalThresholdAmount,
        BigDecimal countVarianceApprovalPct,
        boolean transferApprovalRequired,
        boolean allowManualConsumptionReverse,
        long version
) {
}
