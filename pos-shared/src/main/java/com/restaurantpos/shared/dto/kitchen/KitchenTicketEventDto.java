package com.restaurantpos.shared.dto.kitchen;

import java.time.Instant;
import java.util.UUID;

public record KitchenTicketEventDto(
        UUID id,
        UUID kitchenTicketId,
        UUID kitchenTicketItemId,
        UUID actorUserId,
        String eventType,
        String previousStatus,
        String newStatus,
        String reason,
        String detailsJson,
        Instant occurredAt
) {
}
