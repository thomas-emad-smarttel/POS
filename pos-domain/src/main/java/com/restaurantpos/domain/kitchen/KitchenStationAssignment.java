package com.restaurantpos.domain.kitchen;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.util.UUID;

public record KitchenStationAssignment(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID kitchenStationId,
        UUID userId,
        UUID terminalId,
        boolean active
) {
}
