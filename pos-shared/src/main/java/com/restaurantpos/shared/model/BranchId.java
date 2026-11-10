package com.restaurantpos.shared.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed branch identifier for branch-level isolation.
 */
public record BranchId(UUID value) {

    public BranchId {
        Objects.requireNonNull(value, "branchId must not be null");
    }

    public static BranchId of(UUID value) {
        return new BranchId(value);
    }

    public static BranchId fromString(String raw) {
        return new BranchId(UUID.fromString(Objects.requireNonNull(raw, "branchId string must not be null")));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
