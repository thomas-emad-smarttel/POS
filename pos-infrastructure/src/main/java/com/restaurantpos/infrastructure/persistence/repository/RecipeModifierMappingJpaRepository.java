package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.RecipeModifierMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecipeModifierMappingJpaRepository extends JpaRepository<RecipeModifierMappingEntity, UUID> {
    List<RecipeModifierMappingEntity> findByRecipeVersionId(UUID recipeVersionId);
}
