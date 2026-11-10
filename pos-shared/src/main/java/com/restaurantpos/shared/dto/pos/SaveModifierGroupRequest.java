package com.restaurantpos.shared.dto.pos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveModifierGroupRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 150) String name,
        @Min(0) int minSelections,
        @Min(1) int maxSelections,
        boolean required,
        boolean active
) {
}
