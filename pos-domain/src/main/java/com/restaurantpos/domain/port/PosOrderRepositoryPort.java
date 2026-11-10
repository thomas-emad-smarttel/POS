package com.restaurantpos.domain.port;

import com.restaurantpos.domain.order.OrderAggregate;
import com.restaurantpos.domain.order.OrderStatus;
import com.restaurantpos.shared.dto.pos.OrderSummaryDto;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PosOrderRepositoryPort {
    OrderAggregate save(OrderAggregate aggregate);
    Optional<OrderAggregate> findByIdScoped(UUID orderId, TenantId tenantId, BranchId branchId);
    List<OrderSummaryDto> searchBranchOrders(TenantId tenantId, BranchId branchId, OrderStatus statusFilter, Boolean heldFilter, int page, int size);
    void recordStatusHistory(UUID id, TenantId tenantId, BranchId branchId, UUID orderId, OrderStatus fromStatus, OrderStatus toStatus, UUID changedByUserId, String reason, Instant changedAt);
}
