package com.restaurantpos.domain.order;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable historical line-item snapshot preserved even if menu items change later.
 */
public record OrderItemSnapshot(
        UUID id,
        UUID productId,
        String skuSnapshot,
        String productNameSnapshot,
        String variantNameSnapshot,
        BigDecimal quantity,
        BigDecimal unitPriceSnapshot,
        BigDecimal taxRateSnapshot,
        BigDecimal taxAmount,
        BigDecimal discountAmount,
        BigDecimal lineTotal,
        long version
) {
    public OrderItemSnapshot {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(skuSnapshot, "skuSnapshot must not be null");
        Objects.requireNonNull(productNameSnapshot, "productNameSnapshot must not be null");
        Objects.requireNonNull(quantity, "quantity must not be null");
        Objects.requireNonNull(unitPriceSnapshot, "unitPriceSnapshot must not be null");
        Objects.requireNonNull(lineTotal, "lineTotal must not be null");
    }
}
