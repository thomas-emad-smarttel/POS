package com.restaurantpos.domain.port;

import com.restaurantpos.domain.security.RoleDefinition;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.shared.security.PermissionCode;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Outbound persistence port for tenant-isolated RBAC roles and role-permission mappings.
 */
public interface RoleRepositoryPort {
    RoleDefinition save(RoleDefinition role);

    Optional<RoleDefinition> findByIdAndTenant(UUID roleId, TenantId tenantId);

    Optional<RoleDefinition> findByCodeAndTenant(String code, TenantId tenantId);

    List<RoleDefinition> findByTenant(TenantId tenantId, int page, int size);

    void replaceRolePermissions(TenantId tenantId, UUID roleId, Set<PermissionCode> permissions);
}
