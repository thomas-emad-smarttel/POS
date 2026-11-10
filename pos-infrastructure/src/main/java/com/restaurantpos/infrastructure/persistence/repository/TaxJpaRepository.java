package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.TaxEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxJpaRepository extends JpaRepository<TaxEntity, UUID> {
    Optional<TaxEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    List<TaxEntity> findByTenantIdOrderByNameAsc(UUID tenantId);
}
