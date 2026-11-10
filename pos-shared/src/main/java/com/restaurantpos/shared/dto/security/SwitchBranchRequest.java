package com.restaurantpos.shared.dto.security;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SwitchBranchRequest(
        @NotNull UUID branchId
) {
}
