package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.InventoryProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InventoryProcessedEventJpaRepository extends JpaRepository<InventoryProcessedEventEntity, UUID> {
}
