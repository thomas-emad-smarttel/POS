package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.BranchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchJpaRepository extends JpaRepository<BranchEntity, UUID> {
    Optional<BranchEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    List<BranchEntity> findByTenantIdOrderByNameAsc(UUID tenantId);
}
