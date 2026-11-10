package com.restaurantpos.domain.inventory;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.util.UUID;

public record InventoryLocation(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        String code,
        String name,
        String locationType,
        boolean defaultLocation,
        boolean active,
        long version
) {
}
