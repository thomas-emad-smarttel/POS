package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.RecipeVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecipeVersionJpaRepository extends JpaRepository<RecipeVersionEntity, UUID> {
    List<RecipeVersionEntity> findByRecipeIdOrderByVersionNumberAsc(UUID recipeId);
}
