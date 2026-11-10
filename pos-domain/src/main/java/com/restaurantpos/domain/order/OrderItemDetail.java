package com.restaurantpos.domain.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OrderItemDetail(
        UUID id,
        UUID productId,
        UUID productVariantId,
        UUID taxId,
        String taxNameSnapshot,
        boolean taxInclusive,
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
        List<OrderItemModifierSnapshot> modifiers,
        long version
) {
    public OrderItemDetail {
        Objects.requireNonNull(id);
        Objects.requireNonNull(skuSnapshot);
        Objects.requireNonNull(productNameSnapshot);
        Objects.requireNonNull(quantity);
        Objects.requireNonNull(unitPriceSnapshot);
        Objects.requireNonNull(lineTotal);
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
    }
}
