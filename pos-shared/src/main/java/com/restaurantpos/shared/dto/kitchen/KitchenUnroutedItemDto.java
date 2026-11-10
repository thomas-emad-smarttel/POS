package com.restaurantpos.shared.dto.kitchen;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record KitchenUnroutedItemDto(
        UUID id,
        UUID outboxEventId,
        UUID orderId,
        String orderNumber,
        UUID orderItemId,
        UUID productId,
        String productNameSnapshot,
        BigDecimal quantity,
        String modifiersSummary,
        String status,
        UUID resolvedStationId,
        Instant createdAt
) {
}
