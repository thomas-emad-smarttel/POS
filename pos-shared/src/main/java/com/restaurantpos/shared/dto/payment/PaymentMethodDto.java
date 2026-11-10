package com.restaurantpos.shared.dto.payment;

import java.util.UUID;

public record PaymentMethodDto(
        UUID id,
        String code,
        String name,
        String methodType,
        boolean opensDrawer,
        boolean active
) {
}
