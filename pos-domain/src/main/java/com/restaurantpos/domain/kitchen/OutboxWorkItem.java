package com.restaurantpos.domain.kitchen;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.time.Instant;
import java.util.UUID;

public record OutboxWorkItem(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String idempotencyKey,
        String payloadJson,
        String status,
        int retryCount,
        Instant nextAttemptAt,
        Instant createdAt
) {
}
