package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.IngredientEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IngredientJpaRepository extends JpaRepository<IngredientEntity, UUID> {
    List<IngredientEntity> findByTenantIdOrderByNameAsc(UUID tenantId);
    Optional<IngredientEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
