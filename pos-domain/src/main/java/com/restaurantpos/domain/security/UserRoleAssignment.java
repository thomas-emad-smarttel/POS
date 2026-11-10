package com.restaurantpos.domain.security;

import com.restaurantpos.shared.model.TenantId;

import java.util.Objects;
import java.util.UUID;

/**
 * Scoped assignment of a role to a user (tenant-wide when {@code branchId} is null, or branch-specific).
 */
public record UserRoleAssignment(
        UUID id,
        TenantId tenantId,
        UUID userId,
        UUID roleId,
        String roleCode,
        String roleName,
        UUID branchId
) {
    public UserRoleAssignment {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(roleId, "roleId must not be null");
    }
}
