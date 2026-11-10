package com.restaurantpos.domain.port;

import com.restaurantpos.domain.security.UserSession;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound persistence port for server-side session lifecycle management.
 */
public interface UserSessionRepositoryPort {
    UserSession create(UserSession session);

    Optional<UserSession> findByTokenHash(String tokenHash);

    Optional<UserSession> findByIdAndTenant(UUID sessionId, TenantId tenantId);

    List<UserSession> findActiveByUser(TenantId tenantId, UUID userId);

    List<UserSession> findByTenant(TenantId tenantId, int page, int size);

    void updateActiveBranch(UUID sessionId, TenantId tenantId, BranchId branchId);

    void revokeSession(UUID sessionId, TenantId tenantId, Instant revokedAt);

    void revokeAllForUser(TenantId tenantId, UUID userId, Instant revokedAt);
}
