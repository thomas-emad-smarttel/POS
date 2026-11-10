package com.restaurantpos.application.security;

import com.restaurantpos.domain.exception.UnauthorizedOperationException;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.shared.security.PermissionCode;

import java.util.Objects;

/**
 * Centralized application-layer authorization guard enforcing tenant, branch, and granular RBAC permissions.
 */
public final class PermissionPolicy {

    private PermissionPolicy() {
    }

    public static void requireBranchPermission(
            AccessPrincipal principal,
            TenantId expectedTenantId,
            BranchId targetBranchId,
            PermissionCode requiredPermission
    ) {
        Objects.requireNonNull(expectedTenantId, "expectedTenantId must not be null");
        Objects.requireNonNull(targetBranchId, "targetBranchId must not be null");
        Objects.requireNonNull(requiredPermission, "requiredPermission must not be null");

        if (principal == null || !principal.active()) {
            throw new UnauthorizedOperationException(requiredPermission, "Active authenticated principal is required");
        }
        if (!expectedTenantId.equals(principal.tenantId())) {
            throw new UnauthorizedOperationException(requiredPermission, "Tenant isolation boundary violation");
        }
        if (!principal.canAccessBranch(targetBranchId)) {
            throw new UnauthorizedOperationException(
                    requiredPermission,
                    "Principal is not authorized for branch " + targetBranchId
            );
        }
        if (!principal.hasPermission(requiredPermission)) {
            throw new UnauthorizedOperationException(
                    requiredPermission,
                    "Missing required permission: " + requiredPermission.code()
            );
        }
    }
}
