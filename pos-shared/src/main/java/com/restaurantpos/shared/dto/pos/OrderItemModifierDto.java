package com.restaurantpos.shared.dto.pos;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemModifierDto(
        UUID id,
        UUID modifierId,
        String modifierNameSnapshot,
        BigDecimal unitPriceSnapshot,
        int quantity
) {
}
