package com.restaurantpos.domain.security;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.shared.security.PermissionCode;

import java.util.Set;
import java.util.UUID;

/**
 * Pure domain representation of an authenticated principal's server-resolved access scope.
 */
public record AccessPrincipal(
        UUID userId,
        String username,
        TenantId tenantId,
        BranchId activeBranchId,
        UUID terminalId,
        UUID sessionId,
        Set<BranchId> allowedBranches,
        Set<String> assignedRoles,
        Set<PermissionCode> effectivePermissions,
        boolean active
) {
    public AccessPrincipal {
        allowedBranches = allowedBranches == null ? Set.of() : Set.copyOf(allowedBranches);
        assignedRoles = assignedRoles == null ? Set.of() : Set.copyOf(assignedRoles);
        effectivePermissions = effectivePermissions == null ? Set.of() : Set.copyOf(effectivePermissions);
    }

    public AccessPrincipal(
            UUID userId,
            String username,
            TenantId tenantId,
            Set<BranchId> allowedBranches,
            Set<PermissionCode> effectivePermissions,
            boolean active
    ) {
        this(userId, username, tenantId, null, null, null, allowedBranches, Set.of(), effectivePermissions, active);
    }

    public boolean canAccessBranch(BranchId branchId) {
        return active && branchId != null && allowedBranches.contains(branchId);
    }

    public boolean hasPermission(PermissionCode permission) {
        return active && permission != null && effectivePermissions.contains(permission);
    }
}
