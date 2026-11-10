package com.restaurantpos.domain.port;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Emits reliable inventory consumption/refire outbox events inside the calling database transaction.
 */
public interface InventoryDispatchPort {

    default void dispatchKitchenPrepStarted(
            TenantId tenantId,
            BranchId branchId,
            UUID orderId,
            UUID kitchenTicketId,
            UUID actorUserId,
            List<InventoryPayloadCodecPort.OrderPrepItemSnapshot> items
    ) {
    }

    default void dispatchKitchenRefire(
            TenantId tenantId,
            BranchId branchId,
            UUID orderId,
            UUID originalTicketId,
            UUID refireTicketId,
            UUID actorUserId,
            String reason,
            List<InventoryPayloadCodecPort.OrderPrepItemSnapshot> items
    ) {
    }
}
