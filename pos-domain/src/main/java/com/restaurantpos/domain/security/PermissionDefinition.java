package com.restaurantpos.domain.security;

import com.restaurantpos.shared.security.PermissionCode;

import java.util.Objects;
import java.util.UUID;

/**
 * Persisted permission catalog item.
 */
public record PermissionDefinition(
        UUID id,
        PermissionCode permission,
        String module,
        String description,
        boolean sensitive
) {
    public PermissionDefinition {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(permission, "permission must not be null");
        Objects.requireNonNull(module, "module must not be null");
        Objects.requireNonNull(description, "description must not be null");
    }
}
