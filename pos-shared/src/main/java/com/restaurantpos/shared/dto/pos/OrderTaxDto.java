package com.restaurantpos.shared.dto.pos;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderTaxDto(
        UUID id,
        UUID taxId,
        String taxNameSnapshot,
        BigDecimal ratePercentSnapshot,
        boolean inclusive,
        BigDecimal taxableAmount,
        BigDecimal taxAmount
) {
}
