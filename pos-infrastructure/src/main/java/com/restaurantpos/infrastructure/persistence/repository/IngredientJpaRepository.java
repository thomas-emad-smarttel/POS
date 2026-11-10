package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.IngredientEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IngredientJpaRepository extends JpaRepository<IngredientEntity, UUID> {
}
