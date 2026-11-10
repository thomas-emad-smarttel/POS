package com.restaurantpos.domain.order;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderDiscountSnapshot(
        UUID id,
        UUID orderItemId,
        String discountCode,
        String discountName,
        String discountType,
        BigDecimal discountValue,
        BigDecimal appliedAmount,
        UUID appliedByUserId,
        UUID approvedByUserId,
        String reason
) {
}
