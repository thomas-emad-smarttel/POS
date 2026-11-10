package com.restaurantpos.domain.inventory;

import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

public record RecipeModifierMapping(
        UUID id,
        TenantId tenantId,
        UUID recipeVersionId,
        UUID modifierId,
        String actionType,
        UUID inventoryItemId,
        UUID subRecipeId,
        UUID unitId,
        BigDecimal quantityDelta,
        BigDecimal scaleFactor,
        String notes
) {
}
