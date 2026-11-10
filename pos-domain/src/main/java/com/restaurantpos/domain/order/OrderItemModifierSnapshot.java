package com.restaurantpos.domain.order;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemModifierSnapshot(
        UUID id,
        UUID modifierId,
        String modifierNameSnapshot,
        BigDecimal unitPriceSnapshot,
        int quantity
) {
}
