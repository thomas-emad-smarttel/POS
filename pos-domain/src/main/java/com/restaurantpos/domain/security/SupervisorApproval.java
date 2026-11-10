package com.restaurantpos.domain.security;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.shared.security.PermissionCode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record of a supervisor approval or rejection for a sensitive action.
 */
public record SupervisorApproval(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID terminalId,
        PermissionCode requiredPermission,
        String targetEntityType,
        String targetEntityId,
        UUID requestedByUserId,
        UUID approvedByUserId,
        String status,
        String reason,
        CorrelationId correlationId,
        Instant expiresAt,
        Instant createdAt
) {
    public SupervisorApproval {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(branchId, "branchId must not be null");
        Objects.requireNonNull(requiredPermission, "requiredPermission must not be null");
        Objects.requireNonNull(requestedByUserId, "requestedByUserId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
