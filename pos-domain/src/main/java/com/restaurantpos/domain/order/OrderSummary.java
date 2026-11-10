package com.restaurantpos.domain.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight projection for paginated order lists without loading line items.
 */
public record OrderSummary(
        UUID id,
        String orderNumber,
        OrderType orderType,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant openedAt,
        long version
) {
}
