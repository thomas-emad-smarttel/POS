package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryJpaRepository extends JpaRepository<CategoryEntity, UUID> {
    Optional<CategoryEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    List<CategoryEntity> findByTenantIdOrderByDisplayOrderAscNameAsc(UUID tenantId);
}
