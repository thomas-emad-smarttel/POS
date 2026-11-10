package com.restaurantpos.domain.port;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.util.UUID;

/**
 * Publishes lightweight branch change notifications for multi-terminal WebSocket refresh.
 */
public interface BranchEventPublishPort {
    void publish(TenantId tenantId, BranchId branchId, String eventType, UUID entityId);
}
