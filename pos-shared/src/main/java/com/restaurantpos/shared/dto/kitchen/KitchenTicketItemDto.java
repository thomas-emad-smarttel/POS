package com.restaurantpos.shared.dto.kitchen;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KitchenTicketItemDto(
        UUID id,
        UUID kitchenTicketId,
        UUID orderItemId,
        String itemNameSnapshot,
        String variantNameSnapshot,
        BigDecimal quantity,
        List<String> selectedModifiers,
        String prepInstructions,
        String notes,
        int expectedPrepMinutes,
        String status,
        Instant startedAt,
        Instant readyAt,
        Instant servedAt,
        String cancelReason,
        long version
) {
}
