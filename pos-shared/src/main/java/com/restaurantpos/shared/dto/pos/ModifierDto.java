package com.restaurantpos.shared.dto.pos;

import java.math.BigDecimal;
import java.util.UUID;

public record ModifierDto(
        UUID id,
        UUID modifierGroupId,
        String code,
        String name,
        BigDecimal priceDelta,
        int displayOrder,
        boolean defaultSelected,
        boolean active,
        long version
) {
}
