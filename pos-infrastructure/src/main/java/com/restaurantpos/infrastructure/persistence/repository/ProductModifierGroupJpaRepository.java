package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.ProductModifierGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductModifierGroupJpaRepository extends JpaRepository<ProductModifierGroupEntity, ProductModifierGroupEntity.ProductModifierGroupId> {
    List<ProductModifierGroupEntity> findByIdProductIdAndTenantIdOrderByDisplayOrderAsc(UUID productId, UUID tenantId);
    void deleteByIdProductIdAndTenantId(UUID productId, UUID tenantId);
}
