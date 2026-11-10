package com.restaurantpos.shared.dto.pos;

import java.util.UUID;

public record DiningTableDto(
        UUID id,
        UUID diningAreaId,
        String diningAreaName,
        String tableCode,
        String displayName,
        int capacity,
        int sortOrder,
        String status,
        UUID activeOrderId,
        boolean active,
        long version
) {
}
