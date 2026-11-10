package com.restaurantpos.shared.dto.inventory;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RecipeCostPreviewDto(
        UUID recipeId,
        UUID recipeVersionId,
        int versionNumber,
        String recipeName,
        BigDecimal yieldQuantity,
        BigDecimal totalIngredientCost,
        BigDecimal unitCostPerYield,
        BigDecimal productSellingPrice,
        BigDecimal estimatedFoodCostPct,
        List<CostLine> lines
) {
    public record CostLine(
            String ingredientName,
            String sourceKind,
            BigDecimal netQuantity,
            BigDecimal grossQuantityWithLoss,
            String unitCode,
            BigDecimal unitCost,
            BigDecimal lineCost
    ) {
    }
}
