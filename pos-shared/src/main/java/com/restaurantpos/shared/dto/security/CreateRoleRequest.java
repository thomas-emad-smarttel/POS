package com.restaurantpos.shared.dto.security;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;

public record CreateRoleRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Z0-9_]+$") String code,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @NotBlank @Pattern(regexp = "^(TENANT|BRANCH)$") String scopeType,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal maxDiscountPct,
        Set<String> permissionCodes
) {
}
