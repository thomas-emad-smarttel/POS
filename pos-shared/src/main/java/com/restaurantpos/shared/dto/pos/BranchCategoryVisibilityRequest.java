package com.restaurantpos.shared.dto.pos;

public record BranchCategoryVisibilityRequest(
        boolean visible,
        Integer displayOrderOverride
) {
}
