package com.restaurantpos.shared.dto.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record LoginRequest(
        @NotBlank @Size(max = 64) String tenantCode,
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(min = 4, max = 128) String password,
        UUID branchId,
        UUID terminalId
) {
}
