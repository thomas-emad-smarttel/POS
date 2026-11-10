package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.DrawerEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DrawerEventJpaRepository extends JpaRepository<DrawerEventEntity, UUID> {
    Page<DrawerEventEntity> findByTenantIdAndBranchIdOrderByOccurredAtDesc(UUID tenantId, UUID branchId, Pageable pageable);
}
