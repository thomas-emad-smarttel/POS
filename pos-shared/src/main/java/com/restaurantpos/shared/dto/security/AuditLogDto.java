package com.restaurantpos.shared.dto.security;

import java.time.Instant;
import java.util.UUID;

public record AuditLogDto(
        UUID id,
        UUID tenantId,
        UUID branchId,
        UUID actorUserId,
        String action,
        String targetEntityType,
        String targetEntityId,
        String detailsJson,
        String correlationId,
        Instant occurredAt
) {
}
