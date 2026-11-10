package com.restaurantpos.shared.dto.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CashSessionDto(
        UUID id,
        UUID branchId,
        UUID terminalId,
        UUID openedByUserId,
        UUID closedByUserId,
        UUID reopenedByUserId,
        String currencyCode,
        BigDecimal openingFloat,
        BigDecimal derivedExpectedCash,
        BigDecimal countedCashAmount,
        BigDecimal varianceAmount,
        String status,
        Instant openedAt,
        Instant closedAt,
        Instant reopenedAt,
        String reopenReason,
        long version,
        List<CashMovementDto> movements
) {
}
