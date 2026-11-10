package com.restaurantpos.shared.dto.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SaveRecipeRequest(
        UUID productId,
        UUID productVariantId,
        boolean branchScoped,
        @NotBlank @Size(max = 160) String recipeName,
        boolean subRecipe,
        @NotNull @DecimalMin("0.0001") BigDecimal yieldQuantity,
        UUID yieldUnitId,
        @NotNull @DecimalMin("0.0000") @DecimalMax("99.9999") BigDecimal prepLossPct,
        @Size(max = 2000) String prepInstructions,
        @Min(0) int expectedPrepMinutes,
        boolean active,
        boolean publishImmediately,
        @Valid List<IngredientLineInput> ingredients,
        @Valid List<ModifierMappingInput> modifierMappings
) {
    public record IngredientLineInput(
            UUID inventoryItemId,
            UUID subRecipeId,
            @NotNull UUID unitId,
            @NotNull @DecimalMin("0.000001") BigDecimal quantity,
            @DecimalMin("0.0000") @DecimalMax("99.9999") BigDecimal wasteLossPct,
            boolean optionalFlag,
            @Size(max = 64) String alternativeGroup,
            @Size(max = 300) String notes,
            int displayOrder
    ) {
    }

    public record ModifierMappingInput(
            @NotNull UUID modifierId,
            @NotBlank @Pattern(regexp = "^(ADD|REMOVE|MULTIPLY|REPLACE)$") String actionType,
            UUID inventoryItemId,
            UUID subRecipeId,
            UUID unitId,
            @DecimalMin("0.000000") BigDecimal quantityDelta,
            @DecimalMin("0.0001") BigDecimal scaleFactor,
            @Size(max = 300) String notes
    ) {
    }
}
