package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.UnitConversionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UnitConversionJpaRepository extends JpaRepository<UnitConversionEntity, UUID> {
    List<UnitConversionEntity> findByTenantId(UUID tenantId);
}
