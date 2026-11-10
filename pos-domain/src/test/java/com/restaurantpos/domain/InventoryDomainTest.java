package com.restaurantpos.domain;

import com.restaurantpos.domain.exception.DomainException;
import com.restaurantpos.domain.inventory.CostingService;
import com.restaurantpos.domain.inventory.InventoryBalance;
import com.restaurantpos.domain.inventory.InventoryItem;
import com.restaurantpos.domain.inventory.Recipe;
import com.restaurantpos.domain.inventory.RecipeCalculationService;
import com.restaurantpos.domain.inventory.RecipeIngredient;
import com.restaurantpos.domain.inventory.RecipeModifierMapping;
import com.restaurantpos.domain.inventory.RecipeVersion;
import com.restaurantpos.domain.inventory.UnitConversion;
import com.restaurantpos.domain.inventory.UnitConversionService;
import com.restaurantpos.domain.inventory.UnitOfMeasure;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryDomainTest {

    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final BranchId BRANCH = BranchId.of(UUID.randomUUID());

    @Test
    void unitConversionSupportsDimensionsPackageOverridesAndRejectsIncompatibleDimensions() {
        UUID gId = UUID.randomUUID();
        UUID kgId = UUID.randomUUID();
        UUID mlId = UUID.randomUUID();
        UUID ltrId = UUID.randomUUID();
        UUID pcsId = UUID.randomUUID();
        UUID boxId = UUID.randomUUID();
        UUID cheeseItemId = UUID.randomUUID();

        List<UnitOfMeasure> units = List.of(
                new UnitOfMeasure(gId, TENANT, "G", "Gram", "WEIGHT", null, BigDecimal.ONE, 0L),
                new UnitOfMeasure(kgId, TENANT, "KG", "Kilogram", "WEIGHT", gId, new BigDecimal("1000"), 0L),
                new UnitOfMeasure(mlId, TENANT, "ML", "Milliliter", "VOLUME", null, BigDecimal.ONE, 0L),
                new UnitOfMeasure(ltrId, TENANT, "L", "Liter", "VOLUME", mlId, new BigDecimal("1000"), 0L),
                new UnitOfMeasure(pcsId, TENANT, "PCS", "Piece", "COUNT", null, BigDecimal.ONE, 0L),
                new UnitOfMeasure(boxId, TENANT, "BOX", "Box", "COUNT", pcsId, new BigDecimal("10"), 0L)
        );
        List<UnitConversion> custom = List.of(
                new UnitConversion(UUID.randomUUID(), TENANT, cheeseItemId, boxId, pcsId, new BigDecimal("24"), true, 0L)
        );

        assertEquals(0, new BigDecimal("1500").compareTo(
                UnitConversionService.convert(new BigDecimal("1.5"), kgId, gId, null, units, List.of())));
        assertEquals(0, new BigDecimal("0.250000").compareTo(
                UnitConversionService.convert(new BigDecimal("250"), mlId, ltrId, null, units, List.of())));
        // Item-specific package override wins over generic count conversion (1 box = 24 pcs for cheese, 10 globally)
        assertEquals(0, new BigDecimal("48").compareTo(
                UnitConversionService.convert(new BigDecimal("2"), boxId, pcsId, cheeseItemId, units, custom)));

        // Incompatible dimension rejected
        assertThrows(DomainException.class, () ->
                UnitConversionService.convert(BigDecimal.ONE, kgId, ltrId, null, units, List.of()));
    }

    @Test
    void recipeExpansionSupportsSubRecipesWasteLossModifierEffectsAndCycleDetection() {
        UUID gId = UUID.randomUUID();
        UUID kgId = UUID.randomUUID();
        UUID pcsId = UUID.randomUUID();
        List<UnitOfMeasure> units = List.of(
                new UnitOfMeasure(gId, TENANT, "G", "Gram", "WEIGHT", null, BigDecimal.ONE, 0L),
                new UnitOfMeasure(kgId, TENANT, "KG", "Kilogram", "WEIGHT", gId, new BigDecimal("1000"), 0L),
                new UnitOfMeasure(pcsId, TENANT, "PCS", "Piece", "COUNT", null, BigDecimal.ONE, 0L)
        );

        UUID beefId = UUID.randomUUID();
        UUID cheeseId = UUID.randomUUID();
        UUID herbId = UUID.randomUUID();
        Map<UUID, InventoryItem> items = Map.of(
                beefId, new InventoryItem(beefId, TENANT, null, null, null, "BEEF", "Beef", null, "RAW_INGREDIENT",
                        null, kgId, BigDecimal.ZERO, BigDecimal.ONE, new BigDecimal("10.00"), "STANDARD", false, false, "DENY", "WEIGHTED_AVERAGE", true, 0L),
                cheeseId, new InventoryItem(cheeseId, TENANT, null, null, null, "CHZ", "Cheese Slice", null, "RAW_INGREDIENT",
                        null, pcsId, BigDecimal.ZERO, BigDecimal.ONE, new BigDecimal("0.50"), "STANDARD", false, false, "DENY", "WEIGHTED_AVERAGE", true, 0L),
                herbId, new InventoryItem(herbId, TENANT, null, null, null, "HRB", "Seasoning", null, "RAW_INGREDIENT",
                        null, gId, BigDecimal.ZERO, BigDecimal.ONE, new BigDecimal("0.10"), "STANDARD", false, false, "DENY", "WEIGHTED_AVERAGE", true, 0L)
        );

        UUID sauceRecipeId = UUID.randomUUID();
        UUID sauceVerId = UUID.randomUUID();
        Recipe sauceRecipe = new Recipe(
                sauceRecipeId, TENANT, null, null, null, "House Sauce", true, sauceVerId, true, true, Instant.now(), 0L,
                List.of(new RecipeVersion(sauceVerId, TENANT, null, sauceRecipeId, 1, "ACTIVE",
                        new BigDecimal("10"), gId, BigDecimal.ZERO, "Mix", 1, BigDecimal.ZERO, true, Instant.now(), null, 0L,
                        List.of(new RecipeIngredient(UUID.randomUUID(), TENANT, sauceVerId, herbId, null, gId,
                                new BigDecimal("10"), BigDecimal.ZERO, false, null, null, 1)),
                        List.of()))
        );

        UUID extraCheeseMod = UUID.randomUUID();
        UUID burgerRecipeId = UUID.randomUUID();
        UUID burgerVerId = UUID.randomUUID();
        RecipeVersion burgerVer = new RecipeVersion(
                burgerVerId, TENANT, null, burgerRecipeId, 1, "ACTIVE",
                BigDecimal.ONE, pcsId, BigDecimal.ZERO, "Grill", 5, BigDecimal.ZERO, true, Instant.now(), null, 0L,
                List.of(
                        new RecipeIngredient(UUID.randomUUID(), TENANT, burgerVerId, beefId, null, gId,
                                new BigDecimal("150"), BigDecimal.ZERO, false, null, null, 1),
                        new RecipeIngredient(UUID.randomUUID(), TENANT, burgerVerId, cheeseId, null, pcsId,
                                BigDecimal.ONE, BigDecimal.ZERO, false, null, null, 2),
                        new RecipeIngredient(UUID.randomUUID(), TENANT, burgerVerId, null, sauceRecipeId, gId,
                                new BigDecimal("20"), BigDecimal.ZERO, false, null, null, 3)
                ),
                List.of(
                        new RecipeModifierMapping(UUID.randomUUID(), TENANT, burgerVerId, extraCheeseMod, "ADD",
                                cheeseId, null, pcsId, BigDecimal.ONE, null, "Extra cheese")
                )
        );
        Recipe burgerRecipe = new Recipe(burgerRecipeId, TENANT, null, UUID.randomUUID(), null, "Burger",
                false, burgerVerId, true, true, Instant.now(), 0L, List.of(burgerVer));

        Map<UUID, Recipe> allRecipes = Map.of(sauceRecipeId, sauceRecipe, burgerRecipeId, burgerRecipe);
        var reqs = RecipeCalculationService.expandRequirements(
                burgerVer, new BigDecimal("2"),
                List.of(new RecipeCalculationService.ModifierSelectionInput(extraCheeseMod, 1)),
                allRecipes, items, Map.of(), units, List.of()
        );

        Map<UUID, BigDecimal> qtyByItem = reqs.stream()
                .collect(java.util.stream.Collectors.toMap(
                        RecipeCalculationService.IngredientRequirement::inventoryItemId,
                        RecipeCalculationService.IngredientRequirement::baseQuantityRequired
                ));
        // 2 burgers * 150g = 300g = 0.3000 kg beef; 2*(1+1) = 4 cheese slices; 2*(20g sauce -> 20g herbs each = 40g)
        assertEquals(0, new BigDecimal("0.3000").compareTo(qtyByItem.get(beefId)));
        assertEquals(0, new BigDecimal("4.0000").compareTo(qtyByItem.get(cheeseId)));
        assertEquals(0, new BigDecimal("40.0000").compareTo(qtyByItem.get(herbId)));

        // Circular sub-recipe detection: sauce referencing burger while burger references sauce
        UUID badSauceVerId = UUID.randomUUID();
        RecipeVersion cyclicSauce = new RecipeVersion(
                badSauceVerId, TENANT, null, sauceRecipeId, 2, "DRAFT",
                BigDecimal.ONE, gId, BigDecimal.ZERO, null, 0, BigDecimal.ZERO, false, null, null, 0L,
                List.of(new RecipeIngredient(UUID.randomUUID(), TENANT, badSauceVerId, null, burgerRecipeId, pcsId,
                        BigDecimal.ONE, BigDecimal.ZERO, false, null, null, 1)),
                List.of()
        );
        assertThrows(DomainException.class, () ->
                RecipeCalculationService.assertNoSubRecipeCycles(sauceRecipeId, cyclicSauce, allRecipes));
    }

    @Test
    void weightedAverageCostingAndBalanceAvailabilityComputeDeterministically() {
        // Existing 10 units @ $4.00 + incoming 10 units @ $6.00 -> WAC = $5.00
        BigDecimal nextWac = CostingService.computeWeightedAverageCost(
                new BigDecimal("10.0000"), new BigDecimal("4.0000"),
                new BigDecimal("10.0000"), new BigDecimal("6.0000"));
        assertEquals(0, new BigDecimal("5.0000").compareTo(nextWac));

        // $3.75 product cost on $15.00 selling price -> 25.00% food cost
        assertEquals(0, new BigDecimal("25.00").compareTo(
                CostingService.computeFoodCostPercentage(new BigDecimal("3.75"), new BigDecimal("15.00"))));

        InventoryBalance bal = new InventoryBalance(
                UUID.randomUUID(), TENANT, BRANCH, UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("12.0000"), new BigDecimal("4.5000"), nextWac, Instant.now(), Instant.now(), 0L);
        assertEquals(0, new BigDecimal("7.5000").compareTo(bal.availableQuantity()));
        assertTrue(CostingService.computeWeightedAverageCost(BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("5.0000"), new BigDecimal("9.2500")).compareTo(new BigDecimal("9.2500")) == 0);
    }
}
