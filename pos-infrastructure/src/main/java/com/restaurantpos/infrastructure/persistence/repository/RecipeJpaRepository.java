package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.RecipeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipeJpaRepository extends JpaRepository<RecipeEntity, UUID> {
    List<RecipeEntity> findByTenantIdOrderByRecipeNameAsc(UUID tenantId);
    Optional<RecipeEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
