package com.restaurantpos.shared.dto.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveInventoryCategoryRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 400) String description,
        int displayOrder,
        boolean active
) {
}
