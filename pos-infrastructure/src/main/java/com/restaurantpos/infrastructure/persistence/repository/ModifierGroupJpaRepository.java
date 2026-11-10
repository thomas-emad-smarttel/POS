package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.ModifierGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModifierGroupJpaRepository extends JpaRepository<ModifierGroupEntity, UUID> {
    Optional<ModifierGroupEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    List<ModifierGroupEntity> findByTenantIdOrderByNameAsc(UUID tenantId);
}
