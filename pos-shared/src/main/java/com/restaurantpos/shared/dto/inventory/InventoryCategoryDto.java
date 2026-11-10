package com.restaurantpos.shared.dto.inventory;

import java.util.UUID;

public record InventoryCategoryDto(
        UUID id,
        String code,
        String name,
        String description,
        int displayOrder,
        boolean active,
        long version
) {
}
