package com.restaurantpos.infrastructure.persistence.adapter;

import com.restaurantpos.domain.port.RoleRepositoryPort;
import com.restaurantpos.domain.security.RoleDefinition;
import com.restaurantpos.infrastructure.persistence.DatabaseExceptionTranslator;
import com.restaurantpos.infrastructure.persistence.entity.PermissionEntity;
import com.restaurantpos.infrastructure.persistence.entity.RoleEntity;
import com.restaurantpos.infrastructure.persistence.entity.RolePermissionEntity;
import com.restaurantpos.infrastructure.persistence.repository.PermissionJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.RoleJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.RolePermissionJpaRepository;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.shared.security.PermissionCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class RoleJpaAdapter implements RoleRepositoryPort {

    private final RoleJpaRepository roleRepository;
    private final PermissionJpaRepository permissionRepository;
    private final RolePermissionJpaRepository rolePermissionRepository;
    private final DatabaseExceptionTranslator translator;

    public RoleJpaAdapter(
            RoleJpaRepository roleRepository,
            PermissionJpaRepository permissionRepository,
            RolePermissionJpaRepository rolePermissionRepository,
            DatabaseExceptionTranslator translator
    ) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.translator = translator;
    }

    @Override
    @Transactional
    public RoleDefinition save(RoleDefinition role) {
        RoleEntity entity = roleRepository.findByIdAndTenantId(role.id(), role.tenantId().value())
                .map(existing -> {
                    existing.setName(role.name());
                    existing.setDescription(role.description());
                    existing.setScopeType(role.scopeType());
                    existing.setActive(role.active());
                    existing.setMaxDiscountPct(role.maxDiscountPct());
                    return existing;
                })
                .orElseGet(() -> new RoleEntity(
                        role.id(),
                        role.tenantId().value(),
                        role.code(),
                        role.name(),
                        role.description(),
                        role.scopeType(),
                        role.systemTemplate(),
                        role.active(),
                        role.maxDiscountPct()
                ));
        RoleEntity saved = translator.execute("save role", () -> roleRepository.saveAndFlush(entity));
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RoleDefinition> findByIdAndTenant(UUID roleId, TenantId tenantId) {
        return roleRepository.findByIdAndTenantId(roleId, tenantId.value()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RoleDefinition> findByCodeAndTenant(String code, TenantId tenantId) {
        return roleRepository.findByCodeAndTenantId(code, tenantId.value()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleDefinition> findByTenant(TenantId tenantId, int page, int size) {
        return roleRepository.findByTenantIdOrderByNameAsc(tenantId.value(), PageRequest.of(page, size))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void replaceRolePermissions(TenantId tenantId, UUID roleId, Set<PermissionCode> permissions) {
        roleRepository.findByIdAndTenantId(roleId, tenantId.value())
                .orElseThrow(() -> new IllegalArgumentException("Role does not belong to tenant"));

        rolePermissionRepository.deleteByIdRoleId(roleId);
        rolePermissionRepository.flush();

        for (PermissionCode code : permissions) {
            PermissionEntity perm = permissionRepository.findByCode(code.code())
                    .orElseThrow(() -> new IllegalStateException("Permission not synced in catalog: " + code.code()));
            rolePermissionRepository.save(new RolePermissionEntity(roleId, perm.getId()));
        }
        rolePermissionRepository.flush();
    }

    private RoleDefinition toDomain(RoleEntity entity) {
        Set<PermissionCode> permissions = rolePermissionRepository.findPermissionCodesByRoleId(entity.getId()).stream()
                .flatMap(code -> PermissionCode.fromCode(code).stream())
                .collect(Collectors.toSet());
        return new RoleDefinition(
                entity.getId(),
                TenantId.of(entity.getTenantId()),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getScopeType(),
                entity.isSystemTemplate(),
                entity.isActive(),
                entity.getMaxDiscountPct(),
                permissions,
                entity.getVersion()
        );
    }
}
