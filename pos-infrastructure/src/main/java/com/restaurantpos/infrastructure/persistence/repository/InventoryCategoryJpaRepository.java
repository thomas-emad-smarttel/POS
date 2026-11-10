package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.InventoryCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryCategoryJpaRepository extends JpaRepository<InventoryCategoryEntity, UUID> {
    List<InventoryCategoryEntity> findByTenantIdOrderByDisplayOrderAscNameAsc(UUID tenantId);
    Optional<InventoryCategoryEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
