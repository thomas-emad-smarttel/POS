package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, UUID> {
    List<PaymentEntity> findByOrderIdAndTenantIdAndBranchIdOrderByPaidAtAsc(UUID orderId, UUID tenantId, UUID branchId);
    Optional<PaymentEntity> findByIdAndTenantIdAndBranchId(UUID id, UUID tenantId, UUID branchId);
    Optional<PaymentEntity> findByTenantIdAndBranchIdAndIdempotencyKey(UUID tenantId, UUID branchId, String idempotencyKey);
}
