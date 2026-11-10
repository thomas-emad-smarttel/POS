package com.restaurantpos.shared.dto.security;

import java.time.Instant;
import java.util.UUID;

public record SessionDto(
        UUID id,
        UUID userId,
        String username,
        UUID branchId,
        UUID terminalId,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt,
        boolean current
) {
}
