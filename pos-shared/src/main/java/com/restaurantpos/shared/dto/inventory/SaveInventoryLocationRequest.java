package com.restaurantpos.shared.dto.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveInventoryLocationRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 120) String name,
        @Pattern(regexp = "^(DRY_STORAGE|COLD_ROOM|FREEZER|BAR|PREP_LINE|MAIN_STORE|OTHER)$") String locationType,
        boolean defaultLocation,
        boolean active
) {
}
