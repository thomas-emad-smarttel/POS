package com.restaurantpos.shared.dto.pos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummaryDto(
        UUID id,
        String orderNumber,
        String orderType,
        String status,
        UUID diningTableId,
        BigDecimal totalAmount,
        boolean held,
        Instant openedAt,
        long version
) {
}
