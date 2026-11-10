package com.restaurantpos.application.inventory;

import com.restaurantpos.application.audit.AuditRecordingService;
import com.restaurantpos.application.security.PermissionPolicy;
import com.restaurantpos.domain.exception.DomainException;
import com.restaurantpos.domain.exception.ResourceNotFoundException;
import com.restaurantpos.domain.exception.UnauthorizedOperationException;
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
import com.restaurantpos.domain.menu.Product;
import com.restaurantpos.domain.port.InventoryRepositoryPort;
import com.restaurantpos.domain.port.MenuRepositoryPort;
import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.shared.dto.inventory.RecipeCostPreviewDto;
import com.restaurantpos.shared.dto.inventory.RecipeDto;
import com.restaurantpos.shared.dto.inventory.SaveRecipeRequest;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.security.AuditAction;
import com.restaurantpos.shared.security.PermissionCode;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manages immutable versioned recipes, sub-recipes, modifier ingredient mappings,
 * circular-dependency checks, publication validation, and weighted average recipe costing.
 */
public class RecipeManagementService {

    private static final MathContext MC = new MathContext(18, RoundingMode.HALF_UP);

    private final InventoryRepositoryPort inventoryRepo;
    private final MenuRepositoryPort menuRepo;
    private final AuditRecordingService auditService;
    private final SystemClockPort clock;

    public RecipeManagementService(
            InventoryRepositoryPort inventoryRepo,
            MenuRepositoryPort menuRepo,
            AuditRecordingService auditService,
            SystemClockPort clock
    ) {
        this.inventoryRepo = inventoryRepo;
        this.menuRepo = menuRepo;
        this.auditService = auditService;
        this.clock = clock;
    }

    public List<RecipeDto> listRecipes(AccessPrincipal actor) {
        BranchId branchId = requireRecipeView(actor);
        boolean canViewCost = actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW);
        Map<UUID, InventoryItem> items = loadItemsMap(actor);
        Map<UUID, UnitOfMeasure> units = loadUnitsMap(actor);
        Map<UUID, Recipe> allRecipes = inventoryRepo.findRecipesByTenant(actor.tenantId()).stream()
                .collect(Collectors.toMap(Recipe::id, Function.identity()));

        return allRecipes.values().stream()
                .filter(r -> r.branchId() == null || r.branchId().equals(branchId))
                .map(r -> toRecipeDto(r, allRecipes, items, units, canViewCost, actor))
                .toList();
    }

    public RecipeDto getRecipe(AccessPrincipal actor, UUID recipeId) {
        BranchId branchId = requireRecipeView(actor);
        Recipe recipe = inventoryRepo.findRecipeScoped(recipeId, actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));
        if (recipe.branchId() != null && !recipe.branchId().equals(branchId)) {
            throw new ResourceNotFoundException("Recipe not found in branch");
        }
        boolean canViewCost = actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW);
        Map<UUID, Recipe> allRecipes = inventoryRepo.findRecipesByTenant(actor.tenantId()).stream()
                .collect(Collectors.toMap(Recipe::id, Function.identity()));
        return toRecipeDto(recipe, allRecipes, loadItemsMap(actor), loadUnitsMap(actor), canViewCost, actor);
    }

    public RecipeDto createOrNewVersion(AccessPrincipal actor, UUID existingRecipeId, SaveRecipeRequest req, CorrelationId corr) {
        BranchId branchId = requireRecipeManage(actor);
        if (req.productId() != null) {
            menuRepo.findProductScoped(req.productId(), actor.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        }

        List<Recipe> allList = inventoryRepo.findRecipesByTenant(actor.tenantId());
        Map<UUID, Recipe> allRecipes = allList.stream().collect(Collectors.toMap(Recipe::id, Function.identity()));
        Map<UUID, InventoryItem> items = loadItemsMap(actor);
        List<UnitOfMeasure> allUnits = inventoryRepo.findUnitsByTenant(actor.tenantId());
        List<UnitConversion> conversions = inventoryRepo.findUnitConversionsByTenant(actor.tenantId());
        Map<UUID, BigDecimal> branchCosts = loadBranchCosts(actor, branchId, items);

        Recipe existing = null;
        if (existingRecipeId != null) {
            existing = inventoryRepo.findRecipeScoped(existingRecipeId, actor.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));
            if (existing.branchId() != null && !existing.branchId().equals(branchId)) {
                throw new ResourceNotFoundException("Recipe not found in branch");
            }
        } else if (req.productId() != null) {
            BranchId scopeBranch = req.branchScoped() ? branchId : null;
            existing = allList.stream()
                    .filter(r -> java.util.Objects.equals(r.productId(), req.productId())
                            && java.util.Objects.equals(r.productVariantId(), req.productVariantId())
                            && java.util.Objects.equals(r.branchId(), scopeBranch))
                    .findFirst()
                    .orElse(null);
        }

        UUID recipeId = existing != null ? existing.id() : UUID.randomUUID();
        List<RecipeVersion> existingVersions = existing != null && existing.versions() != null
                ? existing.versions()
                : List.of();
        int nextVerNum = existingVersions.stream().mapToInt(RecipeVersion::versionNumber).max().orElse(0) + 1;
        UUID newVersionId = UUID.randomUUID();
        Instant now = clock.now();

        List<RecipeIngredient> newIngredients = new ArrayList<>();
        if (req.ingredients() != null) {
            for (int idx = 0; idx < req.ingredients().size(); idx++) {
                SaveRecipeRequest.IngredientLineInput in = req.ingredients().get(idx);
                newIngredients.add(new RecipeIngredient(
                        UUID.randomUUID(),
                        actor.tenantId(),
                        newVersionId,
                        in.inventoryItemId(),
                        in.subRecipeId(),
                        in.unitId(),
                        in.quantity(),
                        in.wasteLossPct() != null ? in.wasteLossPct() : BigDecimal.ZERO,
                        in.optionalFlag(),
                        in.alternativeGroup(),
                        in.notes(),
                        in.displayOrder() > 0 ? in.displayOrder() : idx + 1
                ));
            }
        }

        List<RecipeModifierMapping> newModifiers = new ArrayList<>();
        if (req.modifierMappings() != null) {
            for (SaveRecipeRequest.ModifierMappingInput m : req.modifierMappings()) {
                newModifiers.add(new RecipeModifierMapping(
                        UUID.randomUUID(),
                        actor.tenantId(),
                        newVersionId,
                        m.modifierId(),
                        m.actionType(),
                        m.inventoryItemId(),
                        m.subRecipeId(),
                        m.unitId(),
                        m.quantityDelta() != null ? m.quantityDelta() : BigDecimal.ZERO,
                        m.scaleFactor(),
                        m.notes()
                ));
            }
        }

        RecipeVersion candidate = new RecipeVersion(
                newVersionId,
                actor.tenantId(),
                req.branchScoped() ? branchId : (existing != null ? existing.branchId() : null),
                recipeId,
                nextVerNum,
                req.publishImmediately() ? "ACTIVE" : "DRAFT",
                req.yieldQuantity(),
                req.yieldUnitId(),
                req.prepLossPct(),
                req.prepInstructions(),
                req.expectedPrepMinutes(),
                BigDecimal.ZERO,
                false,
                req.publishImmediately() ? now : null,
                actor.userId(),
                0L,
                newIngredients,
                newModifiers
        );

        // Always block circular sub-recipe cycles immediately, even on DRAFT
        RecipeCalculationService.assertNoSubRecipeCycles(recipeId, candidate, allRecipes);

        if (req.publishImmediately()) {
            RecipeCalculationService.validateForPublication(
                    existing, candidate, allRecipes, items, allUnits, conversions);
        }

        BigDecimal totalCost = BigDecimal.ZERO;
        if (!newIngredients.isEmpty()) {
            try {
                var reqs = RecipeCalculationService.expandRequirements(
                        candidate, candidate.yieldQuantity(), List.of(),
                        allRecipes, items, branchCosts, allUnits, conversions
                );
                totalCost = reqs.stream()
                        .map(RecipeCalculationService.IngredientRequirement::lineCost)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(candidate.yieldQuantity(), CostingService.COST_SCALE, RoundingMode.HALF_UP);
            } catch (DomainException ex) {
                if (req.publishImmediately()) {
                    throw ex;
                }
            }
        }

        RecipeVersion finalizedVersion = new RecipeVersion(
                candidate.id(), candidate.tenantId(), candidate.branchId(), candidate.recipeId(),
                candidate.versionNumber(), candidate.status(), candidate.yieldQuantity(),
                candidate.yieldUnitId(), candidate.prepLossPct(), candidate.prepInstructions(),
                candidate.expectedPrepMinutes(), totalCost, req.publishImmediately(),
                candidate.publishedAt(), candidate.createdByUserId(), 0L,
                candidate.ingredients(), candidate.modifierMappings()
        );

        // Historical versions are NEVER mutated except archiving status when superseded
        List<RecipeVersion> nextVersions = new ArrayList<>();
        for (RecipeVersion rv : existingVersions) {
            if (req.publishImmediately() && "ACTIVE".equals(rv.status())) {
                nextVersions.add(new RecipeVersion(
                        rv.id(), rv.tenantId(), rv.branchId(), rv.recipeId(), rv.versionNumber(),
                        "ARCHIVED", rv.yieldQuantity(), rv.yieldUnitId(), rv.prepLossPct(),
                        rv.prepInstructions(), rv.expectedPrepMinutes(), rv.theoreticalCost(),
                        rv.validated(), rv.publishedAt(), rv.createdByUserId(), rv.version(),
                        rv.ingredients(), rv.modifierMappings()
                ));
            } else {
                nextVersions.add(rv);
            }
        }
        nextVersions.add(finalizedVersion);

        UUID activeVerId = req.publishImmediately()
                ? finalizedVersion.id()
                : (existing != null ? existing.activeVersionId() : null);

        Recipe toSave = new Recipe(
                recipeId,
                actor.tenantId(),
                req.branchScoped() ? branchId : (existing != null ? existing.branchId() : null),
                req.productId(),
                req.productVariantId(),
                req.recipeName().trim(),
                req.subRecipe(),
                activeVerId,
                activeVerId != null,
                req.active(),
                now,
                existing != null ? existing.version() : 0L,
                nextVersions
        );

        Recipe saved = inventoryRepo.saveRecipe(toSave);
        auditService.record(actor.tenantId(), branchId, actor.userId(),
                req.publishImmediately() ? AuditAction.RECIPE_VERSION_PUBLISHED : AuditAction.RECIPE_SAVED,
                "Recipe", saved.id().toString(),
                "{\"version\":" + nextVerNum + ",\"published\":" + req.publishImmediately() + "}", corr);

        Map<UUID, Recipe> updatedMap = new HashMap<>(allRecipes);
        updatedMap.put(saved.id(), saved);
        return toRecipeDto(saved, updatedMap, items, loadUnitsMap(actor),
                actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW), actor);
    }

    public RecipeDto publishVersion(AccessPrincipal actor, UUID recipeId, UUID versionId, CorrelationId corr) {
        BranchId branchId = requireRecipeManage(actor);
        Recipe recipe = inventoryRepo.findRecipeScoped(recipeId, actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));
        if (recipe.branchId() != null && !recipe.branchId().equals(branchId)) {
            throw new ResourceNotFoundException("Recipe not found in branch");
        }
        RecipeVersion target = recipe.versions().stream()
                .filter(v -> v.id().equals(versionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Recipe version not found"));

        Map<UUID, Recipe> allRecipes = inventoryRepo.findRecipesByTenant(actor.tenantId()).stream()
                .collect(Collectors.toMap(Recipe::id, Function.identity()));
        Map<UUID, InventoryItem> items = loadItemsMap(actor);
        List<UnitOfMeasure> allUnits = inventoryRepo.findUnitsByTenant(actor.tenantId());
        List<UnitConversion> conversions = inventoryRepo.findUnitConversionsByTenant(actor.tenantId());
        Map<UUID, BigDecimal> branchCosts = loadBranchCosts(actor, branchId, items);

        RecipeCalculationService.validateForPublication(recipe, target, allRecipes, items, allUnits, conversions);

        BigDecimal totalCost = RecipeCalculationService.expandRequirements(
                        target, target.yieldQuantity(), List.of(),
                        allRecipes, items, branchCosts, allUnits, conversions
                ).stream()
                .map(RecipeCalculationService.IngredientRequirement::lineCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(target.yieldQuantity(), CostingService.COST_SCALE, RoundingMode.HALF_UP);

        Instant now = clock.now();
        List<RecipeVersion> nextVersions = new ArrayList<>();
        for (RecipeVersion rv : recipe.versions()) {
            if (rv.id().equals(versionId)) {
                nextVersions.add(new RecipeVersion(
                        rv.id(), rv.tenantId(), rv.branchId(), rv.recipeId(), rv.versionNumber(),
                        "ACTIVE", rv.yieldQuantity(), rv.yieldUnitId(), rv.prepLossPct(),
                        rv.prepInstructions(), rv.expectedPrepMinutes(), totalCost,
                        true, now, rv.createdByUserId(), rv.version(),
                        rv.ingredients(), rv.modifierMappings()
                ));
            } else if ("ACTIVE".equals(rv.status())) {
                nextVersions.add(new RecipeVersion(
                        rv.id(), rv.tenantId(), rv.branchId(), rv.recipeId(), rv.versionNumber(),
                        "ARCHIVED", rv.yieldQuantity(), rv.yieldUnitId(), rv.prepLossPct(),
                        rv.prepInstructions(), rv.expectedPrepMinutes(), rv.theoreticalCost(),
                        rv.validated(), rv.publishedAt(), rv.createdByUserId(), rv.version(),
                        rv.ingredients(), rv.modifierMappings()
                ));
            } else {
                nextVersions.add(rv);
            }
        }

        Recipe saved = inventoryRepo.saveRecipe(new Recipe(
                recipe.id(), recipe.tenantId(), recipe.branchId(), recipe.productId(),
                recipe.productVariantId(), recipe.recipeName(), recipe.subRecipe(),
                versionId, true, recipe.active(), now, recipe.version(), nextVersions
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.RECIPE_VERSION_PUBLISHED,
                "Recipe", saved.id().toString(), "{\"versionId\":\"" + versionId + "\"}", corr);

        allRecipes.put(saved.id(), saved);
        return toRecipeDto(saved, allRecipes, items, loadUnitsMap(actor),
                actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW), actor);
    }

    public RecipeCostPreviewDto previewRecipeCost(AccessPrincipal actor, UUID recipeId, UUID versionId) {
        BranchId branchId = PermissionPolicy.requireBranchPermission(
                actor, actor.tenantId(), actor.activeBranchId(), PermissionCode.INVENTORY_COST_VIEW);
        Recipe recipe = inventoryRepo.findRecipeScoped(recipeId, actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));
        RecipeVersion rv = versionId != null
                ? recipe.versions().stream().filter(v -> v.id().equals(versionId)).findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException("Recipe version not found"))
                : RecipeCalculationService.findActiveOrLatestVersion(recipe);
        if (rv == null) {
            throw new DomainException("INVALID_RECIPE", "Recipe has no versions");
        }

        Map<UUID, Recipe> allRecipes = inventoryRepo.findRecipesByTenant(actor.tenantId()).stream()
                .collect(Collectors.toMap(Recipe::id, Function.identity()));
        Map<UUID, InventoryItem> items = loadItemsMap(actor);
        Map<UUID, UnitOfMeasure> units = loadUnitsMap(actor);
        List<UnitOfMeasure> allUnits = new ArrayList<>(units.values());
        List<UnitConversion> conversions = inventoryRepo.findUnitConversionsByTenant(actor.tenantId());
        Map<UUID, BigDecimal> branchCosts = loadBranchCosts(actor, branchId, items);

        var reqs = RecipeCalculationService.expandRequirements(
                rv, rv.yieldQuantity(), List.of(), allRecipes, items, branchCosts, allUnits, conversions);

        BigDecimal totalCost = BigDecimal.ZERO;
        List<RecipeCostPreviewDto.CostLine> lines = new ArrayList<>();
        for (RecipeCalculationService.IngredientRequirement req : reqs) {
            InventoryItem item = items.get(req.inventoryItemId());
            UnitOfMeasure unit = item != null ? units.get(item.unitId()) : null;
            totalCost = totalCost.add(req.lineCost());
            lines.add(new RecipeCostPreviewDto.CostLine(
                    item != null ? item.name() : "Unknown",
                    "INGREDIENT",
                    req.baseQuantityRequired(),
                    req.baseQuantityRequired(),
                    unit != null ? unit.code() : "?",
                    req.effectiveUnitCost(),
                    req.lineCost()
            ));
        }

        BigDecimal unitCostPerYield = totalCost.divide(rv.yieldQuantity(), CostingService.COST_SCALE, RoundingMode.HALF_UP);
        BigDecimal sellingPrice = BigDecimal.ZERO;
        if (recipe.productId() != null) {
            Product prod = menuRepo.findProductScoped(recipe.productId(), actor.tenantId()).orElse(null);
            if (prod != null) {
                sellingPrice = prod.basePrice();
            }
        }
        BigDecimal foodCostPct = CostingService.computeFoodCostPercentage(unitCostPerYield, sellingPrice);

        return new RecipeCostPreviewDto(
                recipe.id(), rv.id(), rv.versionNumber(), recipe.recipeName(),
                rv.yieldQuantity(), totalCost.setScale(CostingService.COST_SCALE, RoundingMode.HALF_UP),
                unitCostPerYield, sellingPrice, foodCostPct, lines
        );
    }

    private BranchId requireRecipeView(AccessPrincipal actor) {
        if (actor == null || actor.activeBranchId() == null) {
            throw new UnauthorizedOperationException(PermissionCode.INVENTORY_RECIPE_VIEW, "Active branch session required");
        }
        PermissionCode needed = actor.hasPermission(PermissionCode.INVENTORY_RECIPE_VIEW)
                ? PermissionCode.INVENTORY_RECIPE_VIEW : PermissionCode.INVENTORY_VIEW;
        return PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), needed);
    }

    private BranchId requireRecipeManage(AccessPrincipal actor) {
        if (actor == null || actor.activeBranchId() == null) {
            throw new UnauthorizedOperationException(PermissionCode.INVENTORY_RECIPE_MANAGE, "Active branch session required");
        }
        PermissionCode needed = actor.hasPermission(PermissionCode.INVENTORY_RECIPE_MANAGE)
                ? PermissionCode.INVENTORY_RECIPE_MANAGE : PermissionCode.INVENTORY_MANAGE;
        return PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), needed);
    }

    private Map<UUID, InventoryItem> loadItemsMap(AccessPrincipal actor) {
        return inventoryRepo.findItemsByTenant(actor.tenantId()).stream()
                .collect(Collectors.toMap(InventoryItem::id, Function.identity()));
    }

    private Map<UUID, UnitOfMeasure> loadUnitsMap(AccessPrincipal actor) {
        return inventoryRepo.findUnitsByTenant(actor.tenantId()).stream()
                .collect(Collectors.toMap(UnitOfMeasure::id, Function.identity()));
    }

    private Map<UUID, BigDecimal> loadBranchCosts(AccessPrincipal actor, BranchId branchId, Map<UUID, InventoryItem> items) {
        Map<UUID, BigDecimal> costs = new HashMap<>();
        items.forEach((id, item) -> costs.put(id, item.averageCost() != null ? item.averageCost() : BigDecimal.ZERO));
        for (InventoryBalance b : inventoryRepo.findBalancesByBranch(actor.tenantId(), branchId)) {
            if (b.averageUnitCost() != null && b.averageUnitCost().compareTo(BigDecimal.ZERO) > 0) {
                costs.put(b.inventoryItemId(), b.averageUnitCost());
            }
        }
        return costs;
    }

    private RecipeDto toRecipeDto(
            Recipe r,
            Map<UUID, Recipe> allRecipes,
            Map<UUID, InventoryItem> items,
            Map<UUID, UnitOfMeasure> units,
            boolean includeCost,
            AccessPrincipal actor
    ) {
        String prodName = r.productId() != null
                ? menuRepo.findProductScoped(r.productId(), actor.tenantId()).map(Product::name).orElse(r.recipeName())
                : r.recipeName();
        BigDecimal sellingPrice = r.productId() != null
                ? menuRepo.findProductScoped(r.productId(), actor.tenantId()).map(Product::basePrice).orElse(BigDecimal.ZERO)
                : BigDecimal.ZERO;

        RecipeVersion activeVer = RecipeCalculationService.findActiveOrLatestVersion(r);
        List<RecipeDto.RecipeVersionDto> verDtos = r.versions().stream()
                .map(rv -> {
                    UnitOfMeasure yUnit = rv.yieldUnitId() != null ? units.get(rv.yieldUnitId()) : null;
                    List<RecipeDto.RecipeIngredientDto> ingDtos = rv.ingredients().stream()
                            .map(ing -> {
                                InventoryItem item = ing.inventoryItemId() != null ? items.get(ing.inventoryItemId()) : null;
                                Recipe sub = ing.subRecipeId() != null ? allRecipes.get(ing.subRecipeId()) : null;
                                UnitOfMeasure u = units.get(ing.unitId());
                                return new RecipeDto.RecipeIngredientDto(
                                        ing.id(), ing.inventoryItemId(), item != null ? item.sku() : null,
                                        item != null ? item.name() : null, ing.subRecipeId(),
                                        sub != null ? sub.recipeName() : null, ing.unitId(),
                                        u != null ? u.code() : "?", ing.quantity(), ing.wasteLossPct(),
                                        ing.optionalFlag(), ing.alternativeGroup(), ing.notes(),
                                        ing.displayOrder(), null
                                );
                            })
                            .toList();
                    List<RecipeDto.RecipeModifierMappingDto> modDtos = rv.modifierMappings().stream()
                            .map(mm -> {
                                InventoryItem item = mm.inventoryItemId() != null ? items.get(mm.inventoryItemId()) : null;
                                UnitOfMeasure u = mm.unitId() != null ? units.get(mm.unitId()) : null;
                                return new RecipeDto.RecipeModifierMappingDto(
                                        mm.id(), mm.modifierId(), null, mm.actionType(),
                                        mm.inventoryItemId(), item != null ? item.name() : null,
                                        mm.subRecipeId(), mm.unitId(), u != null ? u.code() : null,
                                        mm.quantityDelta(), mm.scaleFactor(), mm.notes()
                                );
                            })
                            .toList();
                    return new RecipeDto.RecipeVersionDto(
                            rv.id(), rv.recipeId(), rv.versionNumber(), rv.status(),
                            rv.yieldQuantity(), rv.yieldUnitId(), yUnit != null ? yUnit.code() : null,
                            rv.prepLossPct(), rv.prepInstructions(), rv.expectedPrepMinutes(),
                            includeCost ? rv.theoreticalCost() : null,
                            rv.validated(), rv.publishedAt(), rv.version(), ingDtos, modDtos
                    );
                })
                .toList();

        BigDecimal theoCost = includeCost && activeVer != null ? activeVer.theoreticalCost() : null;
        BigDecimal foodCostPct = includeCost && activeVer != null
                ? CostingService.computeFoodCostPercentage(activeVer.theoreticalCost(), sellingPrice)
                : null;

        return new RecipeDto(
                r.id(), r.branchId() != null ? r.branchId().value() : null,
                r.productId(), r.productVariantId(), prodName, r.recipeName(),
                r.subRecipe(), r.activeVersionId(),
                activeVer != null ? activeVer.versionNumber() : 0,
                r.validated(), r.active(), theoCost, foodCostPct, r.updatedAt(), r.version(), verDtos
        );
    }
}
