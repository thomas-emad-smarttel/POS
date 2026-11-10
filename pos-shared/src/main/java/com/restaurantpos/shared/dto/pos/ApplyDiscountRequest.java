package com.restaurantpos.shared.dto.pos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ApplyDiscountRequest(
        UUID orderItemId,
        @Size(max = 64) String discountCode,
        @NotBlank @Size(max = 150) String discountName,
        @NotBlank @Pattern(regexp = "^(PERCENTAGE|FIXED_AMOUNT)$") String discountType,
        @NotNull @DecimalMin("0.0001") BigDecimal discountValue,
        @Size(max = 300) String reason,
        UUID supervisorApprovalId,
        long expectedOrderVersion
) {
}
