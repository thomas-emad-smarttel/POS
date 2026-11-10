package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.PaymentAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptJpaRepository extends JpaRepository<PaymentAttemptEntity, UUID> {
    Optional<PaymentAttemptEntity> findByIdAndTenantIdAndBranchId(UUID id, UUID tenantId, UUID branchId);
    Optional<PaymentAttemptEntity> findByTenantIdAndBranchIdAndIdempotencyKey(UUID tenantId, UUID branchId, String idempotencyKey);
    List<PaymentAttemptEntity> findByOrderIdAndTenantIdAndBranchIdOrderByCreatedAtAsc(UUID orderId, UUID tenantId, UUID branchId);
    List<PaymentAttemptEntity> findByTenantIdAndBranchIdAndStatusInOrderByCreatedAtAsc(
            UUID tenantId, UUID branchId, Collection<String> statuses);
}
