package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.ModifierEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ModifierJpaRepository extends JpaRepository<ModifierEntity, UUID> {
    List<ModifierEntity> findByTenantIdAndModifierGroupIdOrderByDisplayOrderAscNameAsc(UUID tenantId, UUID modifierGroupId);
}
