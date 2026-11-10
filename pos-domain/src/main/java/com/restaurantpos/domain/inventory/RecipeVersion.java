package com.restaurantpos.domain.inventory;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecipeVersion(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID recipeId,
        int versionNumber,
        String status,
        BigDecimal yieldQuantity,
        UUID yieldUnitId,
        BigDecimal prepLossPct,
        String prepInstructions,
        int expectedPrepMinutes,
        BigDecimal theoreticalCost,
        boolean validated,
        Instant publishedAt,
        UUID createdByUserId,
        long version,
        List<RecipeIngredient> ingredients,
        List<RecipeModifierMapping> modifierMappings
) {
}
