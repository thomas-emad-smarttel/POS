package com.restaurantpos.domain.security;

import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.shared.security.PermissionCode;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Pure domain representation of a configurable RBAC role and its assigned permissions.
 */
public record RoleDefinition(
        UUID id,
        TenantId tenantId,
        String code,
        String name,
        String description,
        String scopeType,
        boolean systemTemplate,
        boolean active,
        BigDecimal maxDiscountPct,
        Set<PermissionCode> permissions,
        long version
) {
    public RoleDefinition {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(scopeType, "scopeType must not be null");
        Objects.requireNonNull(maxDiscountPct, "maxDiscountPct must not be null");
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public boolean isProtectedSystemAdmin() {
        return systemTemplate && ("SUPER_ADMIN".equals(code) || "RESTAURANT_OWNER".equals(code));
    }
}
