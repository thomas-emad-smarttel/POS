package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.KitchenPrintJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KitchenPrintJobJpaRepository extends JpaRepository<KitchenPrintJobEntity, UUID> {
    Optional<KitchenPrintJobEntity> findByIdAndTenantIdAndBranchId(UUID id, UUID tenantId, UUID branchId);
    Optional<KitchenPrintJobEntity> findByTenantIdAndBranchIdAndIdempotencyKey(UUID tenantId, UUID branchId, String idempotencyKey);
    List<KitchenPrintJobEntity> findByTenantIdAndBranchIdAndStatusInOrderByLastAttemptAtDesc(
            UUID tenantId, UUID branchId, Collection<String> statuses);
    List<KitchenPrintJobEntity> findByKitchenTicketIdAndTenantIdAndBranchIdOrderByCreatedAtAsc(
            UUID kitchenTicketId, UUID tenantId, UUID branchId);
}
