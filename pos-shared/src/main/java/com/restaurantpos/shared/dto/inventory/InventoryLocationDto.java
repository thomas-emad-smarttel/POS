package com.restaurantpos.shared.dto.inventory;

import java.util.UUID;

public record InventoryLocationDto(
        UUID id,
        UUID branchId,
        String code,
        String name,
        String locationType,
        boolean defaultLocation,
        boolean active,
        long version
) {
}
