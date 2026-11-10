package com.restaurantpos.domain.inventory;

import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

public record RecipeIngredient(
        UUID id,
        TenantId tenantId,
        UUID recipeVersionId,
        UUID inventoryItemId,
        UUID subRecipeId,
        UUID unitId,
        BigDecimal quantity,
        BigDecimal wasteLossPct,
        boolean optionalFlag,
        String alternativeGroup,
        String notes,
        int displayOrder
) {
}
