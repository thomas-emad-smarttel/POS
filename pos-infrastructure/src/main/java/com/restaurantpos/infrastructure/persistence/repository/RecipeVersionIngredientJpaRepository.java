package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.RecipeVersionIngredientEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecipeVersionIngredientJpaRepository extends JpaRepository<RecipeVersionIngredientEntity, UUID> {
    List<RecipeVersionIngredientEntity> findByRecipeVersionIdOrderByDisplayOrderAsc(UUID recipeVersionId);
}
