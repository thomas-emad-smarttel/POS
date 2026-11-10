package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.PaymentMethodEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentMethodJpaRepository extends JpaRepository<PaymentMethodEntity, UUID> {
    List<PaymentMethodEntity> findByTenantIdOrderByNameAsc(UUID tenantId);
    Optional<PaymentMethodEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
