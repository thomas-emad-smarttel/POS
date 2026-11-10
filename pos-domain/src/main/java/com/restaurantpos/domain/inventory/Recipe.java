package com.restaurantpos.domain.inventory;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Recipe(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID productId,
        UUID productVariantId,
        String recipeName,
        boolean subRecipe,
        UUID activeVersionId,
        boolean validated,
        boolean active,
        Instant updatedAt,
        long version,
        List<RecipeVersion> versions
) {
}
