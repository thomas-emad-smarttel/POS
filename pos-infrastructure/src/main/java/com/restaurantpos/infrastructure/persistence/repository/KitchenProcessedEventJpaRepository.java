package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.KitchenProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface KitchenProcessedEventJpaRepository extends JpaRepository<KitchenProcessedEventEntity, UUID> {
}
