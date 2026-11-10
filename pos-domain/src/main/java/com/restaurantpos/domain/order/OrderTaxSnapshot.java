package com.restaurantpos.domain.order;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderTaxSnapshot(
        UUID id,
        UUID taxId,
        String taxNameSnapshot,
        BigDecimal ratePercentSnapshot,
        boolean inclusive,
        BigDecimal taxableAmount,
        BigDecimal taxAmount
) {
}
