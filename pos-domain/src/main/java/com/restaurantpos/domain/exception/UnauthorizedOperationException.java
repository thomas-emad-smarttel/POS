package com.restaurantpos.domain.exception;

import com.restaurantpos.shared.security.PermissionCode;

/**
 * Thrown when an actor lacks required RBAC permission or branch/tenant scope.
 */
public class UnauthorizedOperationException extends DomainException {

    private static final long serialVersionUID = 1L;
    private final PermissionCode requiredPermission;

    public UnauthorizedOperationException(PermissionCode requiredPermission, String message) {
        super("AUTH_FORBIDDEN", message);
        this.requiredPermission = requiredPermission;
    }

    public PermissionCode requiredPermission() {
        return requiredPermission;
    }
}
