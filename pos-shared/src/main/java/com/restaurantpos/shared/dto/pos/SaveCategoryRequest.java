package com.restaurantpos.shared.dto.pos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SaveCategoryRequest(
        UUID parentId,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 120) String name,
        int displayOrder,
        @Size(max = 16) String colorHex,
        boolean active
) {
}
