package com.restaurantpos.domain.cash;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashMovement(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID cashSessionId,
        UUID actorUserId,
        CashMovementType movementType,
        BigDecimal amount,
        String reason,
        String correlationId,
        String idempotencyKey,
        Instant occurredAt
) {
}
