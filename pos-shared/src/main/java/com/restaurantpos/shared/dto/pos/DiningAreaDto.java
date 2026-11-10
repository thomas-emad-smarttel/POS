package com.restaurantpos.shared.dto.pos;

import java.util.UUID;

public record DiningAreaDto(
        UUID id,
        String code,
        String name,
        int displayOrder,
        boolean active,
        long version
) {
}
