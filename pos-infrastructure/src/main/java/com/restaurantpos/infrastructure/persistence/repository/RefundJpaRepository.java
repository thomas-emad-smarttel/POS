package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.RefundEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundJpaRepository extends JpaRepository<RefundEntity, UUID> {
    Optional<RefundEntity> findByTenantIdAndBranchIdAndIdempotencyKey(UUID tenantId, UUID branchId, String idempotencyKey);
    List<RefundEntity> findByOrderIdAndTenantIdAndBranchIdOrderByRefundedAtAsc(UUID orderId, UUID tenantId, UUID branchId);
    List<RefundEntity> findByOriginalPaymentIdAndTenantIdAndBranchIdOrderByRefundedAtAsc(
            UUID originalPaymentId, UUID tenantId, UUID branchId);
    List<RefundEntity> findByTenantIdAndBranchIdAndStatusOrderByRefundedAtDesc(UUID tenantId, UUID branchId, String status);
}
