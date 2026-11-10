package com.restaurantpos.shared.dto.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record AssignUserRolesRequest(
        @NotNull @Valid List<RoleScopeEntry> assignments
) {
    public record RoleScopeEntry(
            @NotNull UUID roleId,
            UUID branchId
    ) {
    }
}
