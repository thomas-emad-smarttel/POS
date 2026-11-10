package com.restaurantpos.shared.dto.security;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record AssignUserBranchesRequest(
        @NotNull List<UUID> branchIds
) {
}
