package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, UUID> {
    Page<AuditLogEntity> findByTenantIdAndBranchIdOrderByOccurredAtDesc(UUID tenantId, UUID branchId, Pageable pageable);
}
