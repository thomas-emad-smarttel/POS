package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.ReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReceiptJpaRepository extends JpaRepository<ReceiptEntity, UUID> {
    Optional<ReceiptEntity> findByIdAndTenantIdAndBranchId(UUID id, UUID tenantId, UUID branchId);
    List<ReceiptEntity> findByOrderIdAndTenantIdAndBranchIdOrderByCreatedAtDesc(UUID orderId, UUID tenantId, UUID branchId);
    Optional<ReceiptEntity> findFirstByCashSessionIdAndTenantIdAndBranchIdOrderByCreatedAtDesc(
            UUID cashSessionId, UUID tenantId, UUID branchId);
}
