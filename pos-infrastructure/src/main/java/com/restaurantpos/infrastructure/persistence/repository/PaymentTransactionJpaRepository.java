package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.PaymentTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentTransactionJpaRepository extends JpaRepository<PaymentTransactionEntity, UUID> {
    List<PaymentTransactionEntity> findByPaymentAttemptIdAndTenantIdAndBranchIdOrderByOccurredAtAsc(
            UUID paymentAttemptId, UUID tenantId, UUID branchId);
}
