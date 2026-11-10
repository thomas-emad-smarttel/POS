package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.KitchenRoutingRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KitchenRoutingRuleJpaRepository extends JpaRepository<KitchenRoutingRuleEntity, UUID> {
    Optional<KitchenRoutingRuleEntity> findByIdAndTenantIdAndBranchId(UUID id, UUID tenantId, UUID branchId);
    List<KitchenRoutingRuleEntity> findByTenantIdAndBranchIdOrderByPrecedenceAsc(UUID tenantId, UUID branchId);
}
