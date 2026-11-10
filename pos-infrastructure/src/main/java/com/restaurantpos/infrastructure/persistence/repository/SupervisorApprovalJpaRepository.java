package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.SupervisorApprovalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupervisorApprovalJpaRepository extends JpaRepository<SupervisorApprovalEntity, UUID> {
    Optional<SupervisorApprovalEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
