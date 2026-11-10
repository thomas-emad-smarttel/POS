package com.restaurantpos.shared.dto.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashMovementDto(
        UUID id,
        UUID cashSessionId,
        UUID actorUserId,
        String movementType,
        BigDecimal amount,
        String reason,
        String correlationId,
        Instant occurredAt
) {
}
