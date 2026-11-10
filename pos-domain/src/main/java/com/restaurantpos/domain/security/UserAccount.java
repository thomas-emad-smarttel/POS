package com.restaurantpos.domain.security;

import com.restaurantpos.shared.model.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain representation of an employee user account.
 */
public record UserAccount(
        UUID id,
        TenantId tenantId,
        String username,
        String fullName,
        String email,
        String passwordHash,
        boolean active,
        int failedLoginCount,
        Instant lockedUntil,
        Instant lastLoginAt,
        long version
) {
    public UserAccount {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
