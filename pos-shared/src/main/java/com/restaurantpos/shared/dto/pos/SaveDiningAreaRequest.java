package com.restaurantpos.shared.dto.pos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveDiningAreaRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 120) String name,
        int displayOrder,
        boolean active
) {
}
