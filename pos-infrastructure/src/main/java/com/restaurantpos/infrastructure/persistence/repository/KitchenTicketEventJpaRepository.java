package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.KitchenTicketEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KitchenTicketEventJpaRepository extends JpaRepository<KitchenTicketEventEntity, UUID> {
    List<KitchenTicketEventEntity> findByKitchenTicketIdAndTenantIdAndBranchIdOrderByOccurredAtAsc(
            UUID kitchenTicketId, UUID tenantId, UUID branchId);
}
