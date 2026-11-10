package com.restaurantpos.shared.dto.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record RecordWasteRequest(
        @NotNull UUID stockLocationId,
        @NotNull UUID inventoryItemId,
        @NotNull UUID unitId,
        @NotNull @DecimalMin("0.0001") BigDecimal quantity,
        @NotBlank @Pattern(regexp = "^(PREPARATION_WASTE|SPOILAGE|EXPIRED|DAMAGED|INCORRECT_PREPARATION|CUSTOMER_RETURN_DISPOSAL|OTHER)$")
        String reasonCode,
        @Size(max = 500) String notes,
        @Size(max = 300) String attachmentRef,
        @NotBlank @Size(max = 128) String idempotencyKey,
        UUID supervisorApprovalId
) {
}
