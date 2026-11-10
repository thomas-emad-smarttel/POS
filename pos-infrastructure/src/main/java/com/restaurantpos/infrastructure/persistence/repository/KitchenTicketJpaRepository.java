package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.KitchenTicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KitchenTicketJpaRepository extends JpaRepository<KitchenTicketEntity, UUID> {
    Optional<KitchenTicketEntity> findByIdAndTenantIdAndBranchId(UUID id, UUID tenantId, UUID branchId);

    List<KitchenTicketEntity> findByTenantIdAndBranchIdAndKitchenStationIdAndStatusInOrderByFiredAtAsc(
            UUID tenantId, UUID branchId, UUID stationId, Collection<String> statuses);

    List<KitchenTicketEntity> findByTenantIdAndBranchIdAndStatusInOrderByFiredAtAsc(
            UUID tenantId, UUID branchId, Collection<String> statuses);

    List<KitchenTicketEntity> findByOrderIdAndTenantIdAndBranchIdOrderByFiredAtAsc(
            UUID orderId, UUID tenantId, UUID branchId);
}
