package com.restaurantpos.domain.cash;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashSession(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID terminalId,
        UUID openedByUserId,
        UUID closedByUserId,
        UUID reopenedByUserId,
        String currencyCode,
        BigDecimal openingFloat,
        BigDecimal expectedCashAmount,
        BigDecimal countedCashAmount,
        BigDecimal varianceAmount,
        CashSessionStatus status,
        Instant openedAt,
        Instant closedAt,
        Instant reopenedAt,
        String reopenReason,
        long version
) {
}
