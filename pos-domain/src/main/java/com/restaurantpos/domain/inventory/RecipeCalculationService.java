package com.restaurantpos.domain.inventory;

import com.restaurantpos.domain.exception.DomainException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pure domain recipe expansion and validation engine:
 * - Detects circular sub-recipe cycles prior to saving/publishing
 * - Applies ingredient waste loss & version-level prep loss without floating-point rounding loss
 * - Resolves sub-recipes recursively and applies modifier ADD/REMOVE/MULTIPLY/REPLACE rules
 * - Produces aggregated ingredient requirements in each item's base unit for deterministic stock deduction.
 */
public final class RecipeCalculationService {

    private static final MathContext MC = new MathContext(18, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public record IngredientRequirement(
            UUID inventoryItemId,
            UUID recipeVersionId,
            BigDecimal baseQuantityRequired,
            BigDecimal effectiveUnitCost,
            BigDecimal lineCost
    ) {
    }

    public record ModifierSelectionInput(UUID modifierId, int quantity) {
    }

    private RecipeCalculationService() {
    }

    public static void assertNoSubRecipeCycles(
            UUID rootRecipeId,
            RecipeVersion candidateVersion,
            Map<UUID, Recipe> allRecipesById
    ) {
        Set<UUID> stack = new HashSet<>();
        if (rootRecipeId != null) {
            stack.add(rootRecipeId);
        }
        validateVersionSubRecipes(candidateVersion, allRecipesById, stack);
    }

    private static void validateVersionSubRecipes(
            RecipeVersion version,
            Map<UUID, Recipe> allRecipesById,
            Set<UUID> activeStack
    ) {
        if (version == null || version.ingredients() == null) {
            return;
        }
        for (RecipeIngredient ing : version.ingredients()) {
            if (ing.subRecipeId() != null) {
                if (!activeStack.add(ing.subRecipeId())) {
                    throw new DomainException(
                            "CIRCULAR_SUB_RECIPE",
                            "Circular sub-recipe dependency detected involving recipe " + ing.subRecipeId()
                    );
                }
                Recipe child = allRecipesById.get(ing.subRecipeId());
                if (child == null) {
                    throw new DomainException("INVALID_RECIPE", "Referenced sub-recipe not found: " + ing.subRecipeId());
                }
                RecipeVersion childVer = findActiveOrLatestVersion(child);
                if (childVer != null) {
                    validateVersionSubRecipes(childVer, allRecipesById, activeStack);
                }
                activeStack.remove(ing.subRecipeId());
            }
        }
    }

    public static void validateForPublication(
            Recipe recipe,
            RecipeVersion version,
            Map<UUID, Recipe> allRecipesById,
            Map<UUID, InventoryItem> itemsById,
            List<UnitOfMeasure> allUnits,
            List<UnitConversion> customConversions
    ) {
        if (version == null) {
            throw new DomainException("INVALID_RECIPE", "Recipe version is required");
        }
        if (version.yieldQuantity() == null || version.yieldQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("INVALID_RECIPE", "Recipe yield quantity must be positive");
        }
        if (version.ingredients() == null || version.ingredients().isEmpty()) {
            throw new DomainException("INVALID_RECIPE", "Recipe must define at least one ingredient or sub-recipe before publishing");
        }
        assertNoSubRecipeCycles(recipe != null ? recipe.id() : null, version, allRecipesById);

        for (RecipeIngredient ing : version.ingredients()) {
            if ((ing.inventoryItemId() == null) == (ing.subRecipeId() == null)) {
                throw new DomainException("INVALID_RECIPE", "Each recipe line must reference either an inventory item or a sub-recipe");
            }
            if (ing.quantity() == null || ing.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new DomainException("INVALID_RECIPE", "Recipe ingredient quantity must be positive");
            }
            if (ing.inventoryItemId() != null) {
                InventoryItem item = itemsById.get(ing.inventoryItemId());
                if (item == null || !item.active()) {
                    throw new DomainException("INVALID_RECIPE", "Inactive or missing inventory item in recipe");
                }
                UnitConversionService.convert(ing.quantity(), ing.unitId(), item.unitId(), item.id(), allUnits, customConversions);
            } else {
                Recipe sub = allRecipesById.get(ing.subRecipeId());
                RecipeVersion subVer = findActiveOrLatestVersion(sub);
                if (sub == null || subVer == null || !sub.active()) {
                    throw new DomainException("INVALID_RECIPE", "Sub-recipe must exist and have an active version");
                }
            }
        }
    }

    public static List<IngredientRequirement> expandRequirements(
            RecipeVersion version,
            BigDecimal soldUnits,
            List<ModifierSelectionInput> modifierSelections,
            Map<UUID, Recipe> allRecipesById,
            Map<UUID, InventoryItem> itemsById,
            Map<UUID, BigDecimal> branchAvgCosts,
            List<UnitOfMeasure> allUnits,
            List<UnitConversion> customConversions
    ) {
        Map<UUID, BigDecimal> aggregatedBaseQtys = new LinkedHashMap<>();
        expandRecursive(
                version,
                soldUnits,
                modifierSelections != null ? modifierSelections : List.of(),
                allRecipesById,
                itemsById,
                allUnits,
                customConversions,
                aggregatedBaseQtys,
                new HashSet<>(List.of(version.recipeId()))
        );

        return aggregatedBaseQtys.entrySet().stream()
                .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) > 0)
                .map(e -> {
                    UUID itemId = e.getKey();
                    BigDecimal qty = e.getValue().setScale(UnitConversionService.QUANTITY_SCALE, RoundingMode.HALF_UP);
                    if (qty.compareTo(BigDecimal.ZERO) == 0 && e.getValue().compareTo(BigDecimal.ZERO) > 0) {
                        qty = new BigDecimal("0.0001"); // Prevent small micro-ingredients from rounding away to 0
                    }
                    InventoryItem item = itemsById.get(itemId);
                    BigDecimal unitCost = branchAvgCosts != null && branchAvgCosts.containsKey(itemId)
                            ? branchAvgCosts.get(itemId)
                            : (item != null && item.averageCost() != null ? item.averageCost() : BigDecimal.ZERO);
                    BigDecimal lineCost = qty.multiply(unitCost, MC).setScale(CostingService.COST_SCALE, RoundingMode.HALF_UP);
                    return new IngredientRequirement(itemId, version.id(), qty, unitCost, lineCost);
                })
                .toList();
    }

    private static void expandRecursive(
            RecipeVersion version,
            BigDecimal requestedYieldQty,
            List<ModifierSelectionInput> modifierSelections,
            Map<UUID, Recipe> allRecipesById,
            Map<UUID, InventoryItem> itemsById,
            List<UnitOfMeasure> allUnits,
            List<UnitConversion> customConversions,
            Map<UUID, BigDecimal> outBaseQtys,
            Set<UUID> visitedRecipes
    ) {
        BigDecimal yieldQty = version.yieldQuantity() != null && version.yieldQuantity().compareTo(BigDecimal.ZERO) > 0
                ? version.yieldQuantity()
                : BigDecimal.ONE;
        BigDecimal yieldScale = requestedYieldQty.divide(yieldQty, 12, RoundingMode.HALF_UP);
        BigDecimal versionLossFactor = grossFactorFromLossPct(version.prepLossPct());

        Map<UUID, BigDecimal> directItemQty = new LinkedHashMap<>();
        Map<UUID, BigDecimal> directSubRecipeQty = new LinkedHashMap<>();

        for (RecipeIngredient ing : version.ingredients()) {
            BigDecimal lineGross = ing.quantity()
                    .multiply(yieldScale, MC)
                    .multiply(grossFactorFromLossPct(ing.wasteLossPct()), MC)
                    .multiply(versionLossFactor, MC);

            if (ing.inventoryItemId() != null) {
                InventoryItem item = itemsById.get(ing.inventoryItemId());
                if (item != null) {
                    BigDecimal inBase = UnitConversionService.convert(
                            lineGross, ing.unitId(), item.unitId(), item.id(), allUnits, customConversions);
                    directItemQty.merge(item.id(), inBase, BigDecimal::add);
                }
            } else if (ing.subRecipeId() != null) {
                directSubRecipeQty.merge(ing.subRecipeId(), lineGross, BigDecimal::add);
            }
        }

        // Apply modifier rules on this recipe version
        if (version.modifierMappings() != null && !modifierSelections.isEmpty()) {
            Map<UUID, Integer> modCounts = new HashMap<>();
            for (ModifierSelectionInput m : modifierSelections) {
                modCounts.merge(m.modifierId(), Math.max(1, m.quantity()), Integer::sum);
            }
            for (RecipeModifierMapping map : version.modifierMappings()) {
                Integer modQty = modCounts.get(map.modifierId());
                if (modQty == null || modQty <= 0) {
                    continue;
                }
                BigDecimal countFactor = BigDecimal.valueOf(modQty).multiply(requestedYieldQty, MC);
                switch (map.actionType()) {
                    case "REMOVE" -> {
                        if (map.inventoryItemId() != null) {
                            directItemQty.remove(map.inventoryItemId());
                        }
                        if (map.subRecipeId() != null) {
                            directSubRecipeQty.remove(map.subRecipeId());
                        }
                    }
                    case "MULTIPLY" -> {
                        BigDecimal mult = map.scaleFactor() != null ? map.scaleFactor() : BigDecimal.ONE;
                        if (map.inventoryItemId() != null && directItemQty.containsKey(map.inventoryItemId())) {
                            directItemQty.computeIfPresent(map.inventoryItemId(), (k, v) -> v.multiply(mult, MC));
                        }
                    }
                    case "REPLACE" -> {
                        if (map.inventoryItemId() != null && map.unitId() != null && map.quantityDelta() != null) {
                            InventoryItem item = itemsById.get(map.inventoryItemId());
                            if (item != null) {
                                BigDecimal inBase = UnitConversionService.convert(
                                        map.quantityDelta().multiply(countFactor, MC),
                                        map.unitId(), item.unitId(), item.id(), allUnits, customConversions);
                                directItemQty.put(item.id(), inBase);
                            }
                        }
                    }
                    default -> { // ADD
                        if (map.inventoryItemId() != null && map.unitId() != null && map.quantityDelta() != null
                                && map.quantityDelta().compareTo(BigDecimal.ZERO) > 0) {
                            InventoryItem item = itemsById.get(map.inventoryItemId());
                            if (item != null) {
                                BigDecimal inBase = UnitConversionService.convert(
                                        map.quantityDelta().multiply(countFactor, MC),
                                        map.unitId(), item.unitId(), item.id(), allUnits, customConversions);
                                directItemQty.merge(item.id(), inBase, BigDecimal::add);
                            }
                        } else if (map.subRecipeId() != null && map.quantityDelta() != null
                                && map.quantityDelta().compareTo(BigDecimal.ZERO) > 0) {
                            directSubRecipeQty.merge(map.subRecipeId(), map.quantityDelta().multiply(countFactor, MC), BigDecimal::add);
                        }
                    }
                }
            }
        }

        directItemQty.forEach((itemId, qty) -> outBaseQtys.merge(itemId, qty, BigDecimal::add));

        for (Map.Entry<UUID, BigDecimal> subEntry : directSubRecipeQty.entrySet()) {
            UUID subRecipeId = subEntry.getKey();
            if (!visitedRecipes.add(subRecipeId)) {
                throw new DomainException("CIRCULAR_SUB_RECIPE", "Circular sub-recipe dependency during expansion: " + subRecipeId);
            }
            Recipe sub = allRecipesById.get(subRecipeId);
            RecipeVersion subVer = findActiveOrLatestVersion(sub);
            if (subVer != null) {
                expandRecursive(subVer, subEntry.getValue(), List.of(), allRecipesById, itemsById,
                        allUnits, customConversions, outBaseQtys, visitedRecipes);
            }
            visitedRecipes.remove(subRecipeId);
        }
    }

    private static BigDecimal grossFactorFromLossPct(BigDecimal lossPct) {
        if (lossPct == null || lossPct.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        BigDecimal remaining = HUNDRED.subtract(lossPct, MC);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("INVALID_RECIPE", "Waste/prep loss percentage must be less than 100%");
        }
        return HUNDRED.divide(remaining, 10, RoundingMode.HALF_UP);
    }

    public static RecipeVersion findActiveOrLatestVersion(Recipe recipe) {
        if (recipe == null || recipe.versions() == null || recipe.versions().isEmpty()) {
            return null;
        }
        if (recipe.activeVersionId() != null) {
            for (RecipeVersion rv : recipe.versions()) {
                if (rv.id().equals(recipe.activeVersionId())) {
                    return rv;
                }
            }
        }
        return recipe.versions().getLast();
    }
}
