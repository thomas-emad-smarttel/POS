package com.restaurantpos.shared.dto.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecipeDto(
        UUID id,
        UUID branchId,
        UUID productId,
        UUID productVariantId,
        String productName,
        String recipeName,
        boolean subRecipe,
        UUID activeVersionId,
        int activeVersionNumber,
        boolean validated,
        boolean active,
        BigDecimal theoreticalCost,
        BigDecimal foodCostPercent,
        Instant updatedAt,
        long version,
        List<RecipeVersionDto> versions
) {
    public record RecipeIngredientDto(
            UUID id,
            UUID inventoryItemId,
            String itemSku,
            String itemName,
            UUID subRecipeId,
            String subRecipeName,
            UUID unitId,
            String unitCode,
            BigDecimal quantity,
            BigDecimal wasteLossPct,
            boolean optionalFlag,
            String alternativeGroup,
            String notes,
            int displayOrder,
            BigDecimal computedLineCost
    ) {
    }

    public record RecipeModifierMappingDto(
            UUID id,
            UUID modifierId,
            String modifierName,
            String actionType,
            UUID inventoryItemId,
            String itemName,
            UUID subRecipeId,
            UUID unitId,
            String unitCode,
            BigDecimal quantityDelta,
            BigDecimal scaleFactor,
            String notes
    ) {
    }

    public record RecipeVersionDto(
            UUID id,
            UUID recipeId,
            int versionNumber,
            String status,
            BigDecimal yieldQuantity,
            UUID yieldUnitId,
            String yieldUnitCode,
            BigDecimal prepLossPct,
            String prepInstructions,
            int expectedPrepMinutes,
            BigDecimal theoreticalCost,
            boolean validated,
            Instant publishedAt,
            long version,
            List<RecipeIngredientDto> ingredients,
            List<RecipeModifierMappingDto> modifierMappings
    ) {
    }
}
