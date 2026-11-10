package com.restaurantpos.domain.security;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persisted server-side session record storing only a SHA-256 hash of the bearer token.
 */
public record UserSession(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID userId,
        UUID terminalId,
        String tokenHash,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt
) {
    public UserSession {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public boolean isValidAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
