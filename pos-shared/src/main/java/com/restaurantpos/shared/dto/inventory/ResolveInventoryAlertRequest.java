package com.restaurantpos.shared.dto.inventory;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResolveInventoryAlertRequest(
        long expectedVersion,
        @Pattern(regexp = "^(ACKNOWLEDGED|RESOLVED)$") String targetStatus,
        @Size(max = 300) String resolutionNotes
) {
}
