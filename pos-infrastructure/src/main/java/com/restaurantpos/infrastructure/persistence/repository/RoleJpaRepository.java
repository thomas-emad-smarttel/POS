package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.RoleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleJpaRepository extends JpaRepository<RoleEntity, UUID> {
    Optional<RoleEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<RoleEntity> findByCodeAndTenantId(String code, UUID tenantId);
    Page<RoleEntity> findByTenantIdOrderByNameAsc(UUID tenantId, Pageable pageable);
}
