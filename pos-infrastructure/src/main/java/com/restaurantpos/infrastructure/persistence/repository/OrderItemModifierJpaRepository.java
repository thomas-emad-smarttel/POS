package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.OrderItemModifierEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderItemModifierJpaRepository extends JpaRepository<OrderItemModifierEntity, UUID> {
    List<OrderItemModifierEntity> findByOrderItemIdOrderByCreatedAtAsc(UUID orderItemId);
    void deleteByOrderItemId(UUID orderItemId);
}
