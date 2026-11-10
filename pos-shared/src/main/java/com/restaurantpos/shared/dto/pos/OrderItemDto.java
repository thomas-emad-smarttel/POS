package com.restaurantpos.shared.dto.pos;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderItemDto(
        UUID id,
        UUID productId,
        UUID productVariantId,
        String skuSnapshot,
        String productNameSnapshot,
        String variantNameSnapshot,
        BigDecimal quantity,
        BigDecimal unitPriceSnapshot,
        BigDecimal taxRateSnapshot,
        BigDecimal taxAmount,
        BigDecimal discountAmount,
        BigDecimal lineTotal,
        String kitchenNotes,
        boolean sentToKitchen,
        boolean voided,
        List<OrderItemModifierDto> modifiers,
        long version
) {
}
