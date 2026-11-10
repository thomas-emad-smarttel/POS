package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.DiningAreaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiningAreaJpaRepository extends JpaRepository<DiningAreaEntity, UUID> {
    Optional<DiningAreaEntity> findByIdAndTenantIdAndBranchId(UUID id, UUID tenantId, UUID branchId);
    List<DiningAreaEntity> findByTenantIdAndBranchIdOrderByDisplayOrderAscNameAsc(UUID tenantId, UUID branchId);
}
