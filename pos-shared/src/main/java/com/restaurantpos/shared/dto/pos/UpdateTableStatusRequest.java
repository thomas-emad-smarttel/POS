package com.restaurantpos.shared.dto.pos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateTableStatusRequest(
        @NotBlank @Pattern(regexp = "^(AVAILABLE|OCCUPIED|RESERVED|CLEANING|OUT_OF_SERVICE)$") String status,
        long expectedVersion
) {
}
