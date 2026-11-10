package com.restaurantpos.domain.port;

import com.restaurantpos.domain.order.OrderRecord;
import com.restaurantpos.domain.order.OrderSummary;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound persistence port for orders enforcing tenant/branch isolation and optimistic locking.
 */
public interface OrderRepositoryPort {
    OrderRecord save(OrderRecord order);

    Optional<OrderRecord> findByIdScoped(UUID orderId, TenantId tenantId, BranchId branchId);

    List<OrderSummary> findSummariesByBranch(TenantId tenantId, BranchId branchId, int page, int size);
}
