package com.restaurantpos.domain.inventory;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryPolicy(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
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
    public static InventoryPolicy defaults(UUID id, TenantId tenantId, BranchId branchId) {
        return new InventoryPolicy(
                id,
                tenantId,
                branchId,
                "RESERVE_ON_SEND_CONSUME_ON_PREP",
                false,
                true,
                7,
                new BigDecimal("50.0000"),
                new BigDecimal("100.0000"),
                new BigDecimal("5.0000"),
                true,
                false,
                0L
        );
    }
}
