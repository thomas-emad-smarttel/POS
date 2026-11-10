package com.restaurantpos.shared.dto.pos;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight WebSocket notification telling terminals to refresh authoritative branch state via REST.
 */
public record BranchRealtimeEventDto(
        long sequenceNumber,
        UUID tenantId,
        UUID branchId,
        String eventType,
        UUID entityId,
        Instant occurredAt
) {
}
