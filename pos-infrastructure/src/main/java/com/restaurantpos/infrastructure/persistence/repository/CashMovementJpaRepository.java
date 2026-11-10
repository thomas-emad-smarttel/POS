package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.CashMovementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashMovementJpaRepository extends JpaRepository<CashMovementEntity, UUID> {
    List<CashMovementEntity> findByCashSessionIdAndTenantIdOrderByOccurredAtAsc(UUID cashSessionId, UUID tenantId);
    Optional<CashMovementEntity> findByTenantIdAndBranchIdAndIdempotencyKey(UUID tenantId, UUID branchId, String idempotencyKey);
}
