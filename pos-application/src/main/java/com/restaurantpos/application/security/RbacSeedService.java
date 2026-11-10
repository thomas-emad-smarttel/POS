package com.restaurantpos.application.security;

import com.restaurantpos.domain.port.PermissionCatalogPort;
import com.restaurantpos.domain.port.RoleRepositoryPort;
import com.restaurantpos.domain.security.DefaultRoleTemplate;
import com.restaurantpos.domain.security.RoleDefinition;
import com.restaurantpos.shared.model.TenantId;

import java.util.Objects;
import java.util.UUID;

/**
 * Idempotently syncs the global permission catalog and seeds missing default role templates per tenant
 * without overwriting customized roles or assignments.
 */
public class RbacSeedService {

    private final PermissionCatalogPort permissionCatalogPort;
    private final RoleRepositoryPort roleRepositoryPort;

    public RbacSeedService(PermissionCatalogPort permissionCatalogPort, RoleRepositoryPort roleRepositoryPort) {
        this.permissionCatalogPort = Objects.requireNonNull(permissionCatalogPort);
        this.roleRepositoryPort = Objects.requireNonNull(roleRepositoryPort);
    }

    public void seedTenantDefaults(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        permissionCatalogPort.ensureCatalogSynced();

        for (DefaultRoleTemplate template : DefaultRoleTemplate.values()) {
            if (roleRepositoryPort.findByCodeAndTenant(template.code(), tenantId).isEmpty()) {
                RoleDefinition role = new RoleDefinition(
                        UUID.randomUUID(),
                        tenantId,
                        template.code(),
                        template.displayName(),
                        template.description(),
                        template.scopeType(),
                        true,
                        true,
                        template.maxDiscountPct(),
                        template.defaultPermissions(),
                        0L
                );
                RoleDefinition saved = roleRepositoryPort.save(role);
                roleRepositoryPort.replaceRolePermissions(tenantId, saved.id(), template.defaultPermissions());
            }
        }
    }
}
