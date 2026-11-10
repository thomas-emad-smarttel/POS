package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.UnitEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UnitJpaRepository extends JpaRepository<UnitEntity, UUID> {
}
