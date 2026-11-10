package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.OrderTaxEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderTaxJpaRepository extends JpaRepository<OrderTaxEntity, UUID> {
    List<OrderTaxEntity> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
    void deleteByOrderId(UUID orderId);
}
