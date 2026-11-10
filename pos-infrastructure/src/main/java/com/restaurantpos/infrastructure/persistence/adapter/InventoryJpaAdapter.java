package com.restaurantpos.infrastructure.persistence.adapter;

import com.restaurantpos.domain.inventory.InventoryAlert;
import com.restaurantpos.domain.inventory.InventoryBalance;
import com.restaurantpos.domain.inventory.InventoryCategory;
import com.restaurantpos.domain.inventory.InventoryItem;
import com.restaurantpos.domain.inventory.InventoryLocation;
import com.restaurantpos.domain.inventory.InventoryPolicy;
import com.restaurantpos.domain.inventory.Recipe;
import com.restaurantpos.domain.inventory.RecipeIngredient;
import com.restaurantpos.domain.inventory.RecipeModifierMapping;
import com.restaurantpos.domain.inventory.RecipeVersion;
import com.restaurantpos.domain.inventory.StockCount;
import com.restaurantpos.domain.inventory.StockCountItem;
import com.restaurantpos.domain.inventory.StockMovement;
import com.restaurantpos.domain.inventory.StockMovementType;
import com.restaurantpos.domain.inventory.StockReservation;
import com.restaurantpos.domain.inventory.StockReservationItem;
import com.restaurantpos.domain.inventory.StockTransfer;
import com.restaurantpos.domain.inventory.StockTransferItem;
import com.restaurantpos.domain.inventory.StockWasteRecord;
import com.restaurantpos.domain.inventory.UnitConversion;
import com.restaurantpos.domain.inventory.UnitOfMeasure;
import com.restaurantpos.domain.kitchen.OutboxWorkItem;
import com.restaurantpos.domain.port.InventoryRepositoryPort;
import com.restaurantpos.infrastructure.persistence.DatabaseExceptionTranslator;
import com.restaurantpos.infrastructure.persistence.entity.IngredientEntity;
import com.restaurantpos.infrastructure.persistence.entity.InventoryAlertEntity;
import com.restaurantpos.infrastructure.persistence.entity.InventoryCategoryEntity;
import com.restaurantpos.infrastructure.persistence.entity.InventoryPolicyEntity;
import com.restaurantpos.infrastructure.persistence.entity.InventoryProcessedEventEntity;
import com.restaurantpos.infrastructure.persistence.entity.OutboxEventEntity;
import com.restaurantpos.infrastructure.persistence.entity.RecipeEntity;
import com.restaurantpos.infrastructure.persistence.entity.RecipeModifierMappingEntity;
import com.restaurantpos.infrastructure.persistence.entity.RecipeVersionEntity;
import com.restaurantpos.infrastructure.persistence.entity.RecipeVersionIngredientEntity;
import com.restaurantpos.infrastructure.persistence.entity.StockBalanceEntity;
import com.restaurantpos.infrastructure.persistence.entity.StockCountEntity;
import com.restaurantpos.infrastructure.persistence.entity.StockCountItemEntity;
import com.restaurantpos.infrastructure.persistence.entity.StockLocationEntity;
import com.restaurantpos.infrastructure.persistence.entity.StockMovementEntity;
import com.restaurantpos.infrastructure.persistence.entity.StockReservationEntity;
import com.restaurantpos.infrastructure.persistence.entity.StockReservationItemEntity;
import com.restaurantpos.infrastructure.persistence.entity.StockTransferEntity;
import com.restaurantpos.infrastructure.persistence.entity.StockTransferItemEntity;
import com.restaurantpos.infrastructure.persistence.entity.StockWasteRecordEntity;
import com.restaurantpos.infrastructure.persistence.entity.UnitConversionEntity;
import com.restaurantpos.infrastructure.persistence.entity.UnitEntity;
import com.restaurantpos.infrastructure.persistence.repository.IngredientJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.InventoryAlertJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.InventoryCategoryJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.InventoryPolicyJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.InventoryProcessedEventJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.OutboxEventJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.RecipeJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.RecipeModifierMappingJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.RecipeVersionIngredientJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.RecipeVersionJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.StockBalanceJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.StockCountItemJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.StockCountJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.StockLocationJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.StockMovementJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.StockReservationItemJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.StockReservationJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.StockTransferItemJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.StockTransferJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.StockWasteRecordJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.UnitConversionJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.UnitJpaRepository;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class InventoryJpaAdapter implements InventoryRepositoryPort {

    private final UnitJpaRepository unitRepo;
    private final UnitConversionJpaRepository convRepo;
    private final InventoryCategoryJpaRepository categoryRepo;
    private final StockLocationJpaRepository locationRepo;
    private final IngredientJpaRepository itemRepo;
    private final InventoryPolicyJpaRepository policyRepo;
    private final StockBalanceJpaRepository balanceRepo;
    private final StockMovementJpaRepository movementRepo;
    private final RecipeJpaRepository recipeRepo;
    private final RecipeVersionJpaRepository recipeVersionRepo;
    private final RecipeVersionIngredientJpaRepository recipeIngRepo;
    private final RecipeModifierMappingJpaRepository recipeModRepo;
    private final StockReservationJpaRepository reservationRepo;
    private final StockReservationItemJpaRepository reservationItemRepo;
    private final StockWasteRecordJpaRepository wasteRepo;
    private final StockCountJpaRepository countRepo;
    private final StockCountItemJpaRepository countItemRepo;
    private final StockTransferJpaRepository transferRepo;
    private final StockTransferItemJpaRepository transferItemRepo;
    private final InventoryAlertJpaRepository alertRepo;
    private final InventoryProcessedEventJpaRepository processedEventRepo;
    private final OutboxEventJpaRepository outboxRepo;
    private final DatabaseExceptionTranslator translator;

    public InventoryJpaAdapter(
            UnitJpaRepository unitRepo,
            UnitConversionJpaRepository convRepo,
            InventoryCategoryJpaRepository categoryRepo,
            StockLocationJpaRepository locationRepo,
            IngredientJpaRepository itemRepo,
            InventoryPolicyJpaRepository policyRepo,
            StockBalanceJpaRepository balanceRepo,
            StockMovementJpaRepository movementRepo,
            RecipeJpaRepository recipeRepo,
            RecipeVersionJpaRepository recipeVersionRepo,
            RecipeVersionIngredientJpaRepository recipeIngRepo,
            RecipeModifierMappingJpaRepository recipeModRepo,
            StockReservationJpaRepository reservationRepo,
            StockReservationItemJpaRepository reservationItemRepo,
            StockWasteRecordJpaRepository wasteRepo,
            StockCountJpaRepository countRepo,
            StockCountItemJpaRepository countItemRepo,
            StockTransferJpaRepository transferRepo,
            StockTransferItemJpaRepository transferItemRepo,
            InventoryAlertJpaRepository alertRepo,
            InventoryProcessedEventJpaRepository processedEventRepo,
            OutboxEventJpaRepository outboxRepo,
            DatabaseExceptionTranslator translator
    ) {
        this.unitRepo = unitRepo;
        this.convRepo = convRepo;
        this.categoryRepo = categoryRepo;
        this.locationRepo = locationRepo;
        this.itemRepo = itemRepo;
        this.policyRepo = policyRepo;
        this.balanceRepo = balanceRepo;
        this.movementRepo = movementRepo;
        this.recipeRepo = recipeRepo;
        this.recipeVersionRepo = recipeVersionRepo;
        this.recipeIngRepo = recipeIngRepo;
        this.recipeModRepo = recipeModRepo;
        this.reservationRepo = reservationRepo;
        this.reservationItemRepo = reservationItemRepo;
        this.wasteRepo = wasteRepo;
        this.countRepo = countRepo;
        this.countItemRepo = countItemRepo;
        this.transferRepo = transferRepo;
        this.transferItemRepo = transferItemRepo;
        this.alertRepo = alertRepo;
        this.processedEventRepo = processedEventRepo;
        this.outboxRepo = outboxRepo;
        this.translator = translator;
    }

    @Override
    @Transactional
    public UnitOfMeasure saveUnit(UnitOfMeasure unit) {
        return translator.execute("save unit", () -> {
            UnitEntity e = unitRepo.findByIdAndTenantId(unit.id(), unit.tenantId().value())
                    .orElseGet(() -> new UnitEntity(unit.id(), unit.tenantId().value(), unit.code(), unit.name(), unit.unitType()));
            e.setCode(unit.code());
            e.setName(unit.name());
            e.setUnitType(unit.unitType());
            e.setBaseUnitId(unit.baseUnitId());
            if (unit.conversionFactor() != null) {
                e.setConversionFactor(unit.conversionFactor());
            }
            return toUnitDomain(unitRepo.saveAndFlush(e));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UnitOfMeasure> findUnitScoped(UUID unitId, TenantId tenantId) {
        return unitRepo.findByIdAndTenantId(unitId, tenantId.value()).map(InventoryJpaAdapter::toUnitDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnitOfMeasure> findUnitsByTenant(TenantId tenantId) {
        return unitRepo.findByTenantIdOrderByCodeAsc(tenantId.value()).stream()
                .map(InventoryJpaAdapter::toUnitDomain)
                .toList();
    }

    @Override
    @Transactional
    public UnitConversion saveUnitConversion(UnitConversion c) {
        return translator.execute("save unit conversion", () -> {
            UnitConversionEntity e = convRepo.findById(c.id())
                    .orElseGet(() -> new UnitConversionEntity(c.id(), c.tenantId().value(), c.inventoryItemId(),
                            c.fromUnitId(), c.toUnitId(), c.conversionFactor(), c.active()));
            e.setConversionFactor(c.conversionFactor());
            e.setActive(c.active());
            return toConvDomain(convRepo.saveAndFlush(e));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnitConversion> findUnitConversionsByTenant(TenantId tenantId) {
        return convRepo.findByTenantId(tenantId.value()).stream().map(InventoryJpaAdapter::toConvDomain).toList();
    }

    @Override
    @Transactional
    public InventoryCategory saveCategory(InventoryCategory c) {
        return translator.execute("save inventory category", () -> {
            InventoryCategoryEntity e = categoryRepo.findByIdAndTenantId(c.id(), c.tenantId().value())
                    .orElseGet(() -> new InventoryCategoryEntity(c.id(), c.tenantId().value(),
                            c.code(), c.name(), c.description(), c.displayOrder(), c.active()));
            e.setCode(c.code());
            e.setName(c.name());
            e.setDescription(c.description());
            e.setDisplayOrder(c.displayOrder());
            e.setActive(c.active());
            return toCategoryDomain(categoryRepo.saveAndFlush(e));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryCategory> findCategoryScoped(UUID categoryId, TenantId tenantId) {
        return categoryRepo.findByIdAndTenantId(categoryId, tenantId.value()).map(InventoryJpaAdapter::toCategoryDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryCategory> findCategoriesByTenant(TenantId tenantId) {
        return categoryRepo.findByTenantIdOrderByDisplayOrderAscNameAsc(tenantId.value()).stream()
                .map(InventoryJpaAdapter::toCategoryDomain)
                .toList();
    }

    @Override
    @Transactional
    public InventoryLocation saveLocation(InventoryLocation l) {
        return translator.execute("save stock location", () -> {
            StockLocationEntity e = locationRepo.findByIdAndTenantIdAndBranchId(
                            l.id(), l.tenantId().value(), l.branchId().value())
                    .orElseGet(() -> new StockLocationEntity(l.id(), l.tenantId().value(), l.branchId().value(),
                            l.code(), l.name(), l.defaultLocation()));
            e.setCode(l.code());
            e.setName(l.name());
            e.setLocationType(l.locationType());
            e.setDefaultLocation(l.defaultLocation());
            e.setActive(l.active());
            return toLocationDomain(locationRepo.saveAndFlush(e));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryLocation> findLocationScoped(UUID locationId, TenantId tenantId, BranchId branchId) {
        return locationRepo.findByIdAndTenantIdAndBranchId(locationId, tenantId.value(), branchId.value())
                .map(InventoryJpaAdapter::toLocationDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryLocation> findLocationInTenant(UUID locationId, TenantId tenantId) {
        return locationRepo.findByIdAndTenantId(locationId, tenantId.value())
                .map(InventoryJpaAdapter::toLocationDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryLocation> findLocationsByBranch(TenantId tenantId, BranchId branchId) {
        return locationRepo.findByTenantIdAndBranchIdOrderByNameAsc(tenantId.value(), branchId.value()).stream()
                .map(InventoryJpaAdapter::toLocationDomain)
                .toList();
    }

    @Override
    @Transactional
    public InventoryItem saveItem(InventoryItem item) {
        return translator.execute("save inventory item", () -> {
            Optional<IngredientEntity> existingOpt = itemRepo.findByIdAndTenantId(item.id(), item.tenantId().value());
            IngredientEntity e;
            if (existingOpt.isPresent()) {
                e = existingOpt.get();
                if (e.getVersion() != item.version()) {
                    throw new OptimisticLockingFailureException("Inventory item version mismatch");
                }
            } else {
                e = new IngredientEntity(item.id(), item.tenantId().value(), item.sku(), item.name(), item.unitId());
            }
            e.setBranchId(item.branchId() != null ? item.branchId().value() : null);
            e.setCategoryId(item.categoryId());
            e.setDefaultLocationId(item.defaultLocationId());
            e.setSku(item.sku());
            e.setName(item.name());
            e.setDescription(item.description());
            e.setItemType(item.itemType());
            e.setBarcode(item.barcode());
            e.setUnitId(item.unitId());
            e.setMinimumStockLevel(item.minimumStockLevel());
            e.setReorderLevel(item.reorderLevel());
            e.setAverageCost(item.averageCost());
            e.setTrackingPolicy(item.trackingPolicy());
            e.setExpiryTracking(item.expiryTracking());
            e.setBatchTracking(item.batchTracking());
            e.setNegativeStockPolicy(item.negativeStockPolicy());
            e.setCostingMethod(item.costingMethod());
            e.setActive(item.active());
            return toItemDomain(itemRepo.saveAndFlush(e));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryItem> findItemScoped(UUID itemId, TenantId tenantId) {
        return itemRepo.findByIdAndTenantId(itemId, tenantId.value()).map(InventoryJpaAdapter::toItemDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItem> findItemsByTenant(TenantId tenantId) {
        return itemRepo.findByTenantIdOrderByNameAsc(tenantId.value()).stream()
                .map(InventoryJpaAdapter::toItemDomain)
                .toList();
    }

    @Override
    @Transactional
    public InventoryPolicy savePolicy(InventoryPolicy p) {
        return translator.execute("save inventory policy", () -> {
            InventoryPolicyEntity e = policyRepo.findByTenantIdAndBranchId(p.tenantId().value(), p.branchId().value())
                    .orElseGet(() -> new InventoryPolicyEntity(p.id(), p.tenantId().value(), p.branchId().value()));
            e.setCommitmentMode(p.commitmentMode());
            e.setAllowNegativeStock(p.allowNegativeStock());
            e.setLowStockAlertEnabled(p.lowStockAlertEnabled());
            e.setExpiryWarningDays(p.expiryWarningDays());
            e.setHighWasteThresholdQty(p.highWasteThresholdQty());
            e.setWasteApprovalThresholdAmount(p.wasteApprovalThresholdAmount());
            e.setCountVarianceApprovalPct(p.countVarianceApprovalPct());
            e.setTransferApprovalRequired(p.transferApprovalRequired());
            e.setAllowManualConsumptionReverse(p.allowManualConsumptionReverse());
            return toPolicyDomain(policyRepo.saveAndFlush(e));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryPolicy> findPolicy(TenantId tenantId, BranchId branchId) {
        return policyRepo.findByTenantIdAndBranchId(tenantId.value(), branchId.value())
                .map(InventoryJpaAdapter::toPolicyDomain);
    }

    @Override
    @Transactional
    public InventoryBalance saveBalance(InventoryBalance b) {
        return translator.execute("save inventory balance", () -> {
            Optional<StockBalanceEntity> existingOpt = balanceRepo
                    .findByTenantIdAndBranchIdAndStockLocationIdAndIngredientId(
                            b.tenantId().value(), b.branchId().value(), b.stockLocationId(), b.inventoryItemId());
            StockBalanceEntity e;
            if (existingOpt.isPresent()) {
                e = existingOpt.get();
                if (e.getVersion() != b.version()) {
                    throw new OptimisticLockingFailureException("StockBalance version mismatch");
                }
            } else {
                e = new StockBalanceEntity(b.id(), b.tenantId().value(), b.branchId().value(),
                        b.stockLocationId(), b.inventoryItemId(), b.onHandQuantity());
            }
            e.setQuantityOnHand(b.onHandQuantity());
            e.setQuantityReserved(b.reservedQuantity());
            e.setAverageUnitCost(b.averageUnitCost());
            e.setLastMovementAt(b.lastMovementAt());
            e.setLastCostUpdatedAt(b.lastCostUpdatedAt());
            return toBalanceDomain(balanceRepo.saveAndFlush(e));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryBalance> findBalanceScoped(TenantId tenantId, BranchId branchId, UUID stockLocationId, UUID inventoryItemId) {
        return balanceRepo.findByTenantIdAndBranchIdAndStockLocationIdAndIngredientId(
                        tenantId.value(), branchId.value(), stockLocationId, inventoryItemId)
                .map(InventoryJpaAdapter::toBalanceDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryBalance> findBalancesByBranch(TenantId tenantId, BranchId branchId) {
        return balanceRepo.findByTenantIdAndBranchIdOrderByUpdatedAtDesc(tenantId.value(), branchId.value()).stream()
                .map(InventoryJpaAdapter::toBalanceDomain)
                .toList();
    }

    @Override
    @Transactional
    public StockMovement appendMovement(StockMovement m) {
        return translator.execute("append stock movement", () -> {
            StockMovementEntity e = new StockMovementEntity(
                    m.id(), m.tenantId().value(), m.branchId().value(), m.stockLocationId(), m.inventoryItemId(),
                    m.actorUserId(), m.movementType().name(), m.direction(), m.quantityDelta(),
                    m.unitId(), m.originalQuantity(), m.balanceBefore(), m.balanceAfter(),
                    m.reservedBefore(), m.reservedAfter(), m.unitCost(), m.totalCost(),
                    m.referenceType(), m.referenceId(), m.reason(), m.batchNumber(), m.expiryDate(),
                    m.approvalId(), m.outboxEventId(), m.idempotencyKey(), m.occurredAt()
            );
            return toMovementDomain(movementRepo.saveAndFlush(e));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockMovement> findMovementByIdempotency(TenantId tenantId, BranchId branchId, String idempotencyKey) {
        return movementRepo.findByTenantIdAndBranchIdAndIdempotencyKey(tenantId.value(), branchId.value(), idempotencyKey)
                .map(InventoryJpaAdapter::toMovementDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockMovement> findMovementsByBranch(TenantId tenantId, BranchId branchId, UUID inventoryItemId, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, 500)));
        List<StockMovementEntity> list = inventoryItemId != null
                ? movementRepo.findByTenantIdAndBranchIdAndIngredientIdOrderByOccurredAtDesc(
                        tenantId.value(), branchId.value(), inventoryItemId, page)
                : movementRepo.findByTenantIdAndBranchIdOrderByOccurredAtDesc(tenantId.value(), branchId.value(), page);
        return list.stream().map(InventoryJpaAdapter::toMovementDomain).toList();
    }

    @Override
    @Transactional
    public Recipe saveRecipe(Recipe recipe) {
        return translator.execute("save recipe", () -> {
            RecipeEntity e = recipeRepo.findByIdAndTenantId(recipe.id(), recipe.tenantId().value())
                    .orElseGet(() -> new RecipeEntity(recipe.id(), recipe.tenantId().value()));
            if (recipeRepo.existsById(recipe.id()) && e.getVersion() != recipe.version()) {
                throw new OptimisticLockingFailureException("Recipe version mismatch");
            }
            e.setBranchId(recipe.branchId() != null ? recipe.branchId().value() : null);
            e.setProductId(recipe.productId());
            e.setProductVariantId(recipe.productVariantId());
            e.setRecipeName(recipe.recipeName());
            e.setSubRecipe(recipe.subRecipe());
            e.setActiveVersionId(recipe.activeVersionId());
            e.setValidated(recipe.validated());
            e.setActive(recipe.active());
            RecipeEntity savedHeader = recipeRepo.saveAndFlush(e);

            Map<UUID, RecipeVersionEntity> existingVers = recipeVersionRepo
                    .findByRecipeIdOrderByVersionNumberAsc(savedHeader.getId()).stream()
                    .collect(Collectors.toMap(RecipeVersionEntity::getId, Function.identity()));

            List<RecipeVersion> savedVersions = new ArrayList<>();
            for (RecipeVersion rv : recipe.versions()) {
                RecipeVersionEntity existingVer = existingVers.get(rv.id());
                if (existingVer != null) {
                    // Historical published versions only update archival status
                    if (!existingVer.getStatus().equals(rv.status())) {
                        existingVer.setStatus(rv.status());
                        recipeVersionRepo.saveAndFlush(existingVer);
                    }
                    savedVersions.add(loadVersionDomain(existingVer));
                    continue;
                }
                RecipeVersionEntity newVer = new RecipeVersionEntity(rv.id(), rv.tenantId().value(), savedHeader.getId(), rv.versionNumber());
                newVer.setBranchId(rv.branchId() != null ? rv.branchId().value() : null);
                newVer.setStatus(rv.status());
                newVer.setYieldQuantity(rv.yieldQuantity());
                newVer.setYieldUnitId(rv.yieldUnitId());
                newVer.setPrepLossPct(rv.prepLossPct());
                newVer.setPrepInstructions(rv.prepInstructions());
                newVer.setExpectedPrepMinutes(rv.expectedPrepMinutes());
                newVer.setTheoreticalCost(rv.theoreticalCost());
                newVer.setValidated(rv.validated());
                newVer.setPublishedAt(rv.publishedAt());
                newVer.setCreatedByUserId(rv.createdByUserId());
                RecipeVersionEntity persistedVer = recipeVersionRepo.saveAndFlush(newVer);

                for (RecipeIngredient ing : rv.ingredients()) {
                    recipeIngRepo.saveAndFlush(new RecipeVersionIngredientEntity(
                            ing.id(), ing.tenantId().value(), persistedVer.getId(),
                            ing.inventoryItemId(), ing.subRecipeId(), ing.unitId(),
                            ing.quantity(), ing.wasteLossPct(), ing.optionalFlag(),
                            ing.alternativeGroup(), ing.notes(), ing.displayOrder()
                    ));
                }
                for (RecipeModifierMapping mm : rv.modifierMappings()) {
                    recipeModRepo.saveAndFlush(new RecipeModifierMappingEntity(
                            mm.id(), mm.tenantId().value(), persistedVer.getId(),
                            mm.modifierId(), mm.actionType(), mm.inventoryItemId(),
                            mm.subRecipeId(), mm.unitId(), mm.quantityDelta(),
                            mm.scaleFactor(), mm.notes()
                    ));
                }
                savedVersions.add(loadVersionDomain(persistedVer));
            }
            return toRecipeDomain(savedHeader, savedVersions);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Recipe> findRecipeScoped(UUID recipeId, TenantId tenantId) {
        return recipeRepo.findByIdAndTenantId(recipeId, tenantId.value()).map(this::loadFullRecipe);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Recipe> findActiveRecipeForProduct(TenantId tenantId, BranchId branchId, UUID productId, UUID productVariantId) {
        return findRecipesByTenant(tenantId).stream()
                .filter(r -> r.active() && productId.equals(r.productId())
                        && java.util.Objects.equals(productVariantId, r.productVariantId())
                        && (r.branchId() == null || r.branchId().equals(branchId)))
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Recipe> findRecipesByTenant(TenantId tenantId) {
        return recipeRepo.findByTenantIdOrderByRecipeNameAsc(tenantId.value()).stream()
                .map(this::loadFullRecipe)
                .toList();
    }

    @Override
    @Transactional
    public StockReservation saveReservation(StockReservation res) {
        return translator.execute("save stock reservation", () -> {
            Optional<StockReservationEntity> existingOpt = reservationRepo
                    .findByTenantIdAndBranchIdAndOrderId(res.tenantId().value(), res.branchId().value(), res.orderId());
            StockReservationEntity e;
            if (existingOpt.isPresent()) {
                e = existingOpt.get();
                if (e.getVersion() != res.version()) {
                    throw new OptimisticLockingFailureException("StockReservation version mismatch");
                }
            } else {
                e = new StockReservationEntity(res.id(), res.tenantId().value(), res.branchId().value(), res.orderId());
                e.setSourceEventId(res.sourceEventId());
            }
            e.setStatus(res.status());
            e.setRecipeSnapshotJson(res.recipeSnapshotJson());
            StockReservationEntity saved = reservationRepo.saveAndFlush(e);

            Map<UUID, StockReservationItemEntity> existingItems = reservationItemRepo
                    .findByReservationIdOrderByCreatedAtAsc(saved.getId()).stream()
                    .collect(Collectors.toMap(StockReservationItemEntity::getId, Function.identity()));

            for (StockReservationItem ri : res.items()) {
                StockReservationItemEntity itemEnt = existingItems.getOrDefault(ri.id(),
                        new StockReservationItemEntity(ri.id(), ri.tenantId().value(), ri.branchId().value(),
                                saved.getId(), ri.orderItemId(), ri.inventoryItemId(), ri.idempotencyKey()));
                itemEnt.setKitchenTicketItemId(ri.kitchenTicketItemId());
                itemEnt.setStockLocationId(ri.stockLocationId());
                itemEnt.setRecipeVersionId(ri.recipeVersionId());
                itemEnt.setReservedQuantity(ri.reservedQuantity());
                itemEnt.setConsumedQuantity(ri.consumedQuantity());
                itemEnt.setReleasedQuantity(ri.releasedQuantity());
                itemEnt.setStatus(ri.status());
                reservationItemRepo.saveAndFlush(itemEnt);
            }
            return toReservationDomain(saved, reservationItemRepo.findByReservationIdOrderByCreatedAtAsc(saved.getId()));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockReservation> findReservationByOrder(TenantId tenantId, BranchId branchId, UUID orderId) {
        return reservationRepo.findByTenantIdAndBranchIdAndOrderId(tenantId.value(), branchId.value(), orderId)
                .map(e -> toReservationDomain(e, reservationItemRepo.findByReservationIdOrderByCreatedAtAsc(e.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockReservation> findActiveReservationsByBranch(TenantId tenantId, BranchId branchId) {
        return reservationRepo.findByTenantIdAndBranchIdOrderByCreatedAtDesc(tenantId.value(), branchId.value()).stream()
                .map(e -> toReservationDomain(e, reservationItemRepo.findByReservationIdOrderByCreatedAtAsc(e.getId())))
                .toList();
    }

    @Override
    @Transactional
    public StockWasteRecord saveWasteRecord(StockWasteRecord w) {
        return translator.execute("save waste record", () -> {
            StockWasteRecordEntity e = new StockWasteRecordEntity(
                    w.id(), w.tenantId().value(), w.branchId().value(), w.stockLocationId(),
                    w.inventoryItemId(), w.unitId(), w.originalQuantity(), w.baseQuantity(),
                    w.unitCost(), w.totalCost(), w.reasonCode(), w.notes(), w.attachmentRef(),
                    w.actorUserId(), w.supervisorApprovalId(), w.idempotencyKey(), w.movementId(), w.occurredAt()
            );
            return toWasteDomain(wasteRepo.saveAndFlush(e));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockWasteRecord> findWasteByIdempotency(TenantId tenantId, BranchId branchId, String idempotencyKey) {
        return wasteRepo.findByTenantIdAndBranchIdAndIdempotencyKey(tenantId.value(), branchId.value(), idempotencyKey)
                .map(InventoryJpaAdapter::toWasteDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockWasteRecord> findWasteByBranch(TenantId tenantId, BranchId branchId, int limit) {
        return wasteRepo.findByTenantIdAndBranchIdOrderByOccurredAtDesc(
                        tenantId.value(), branchId.value(), PageRequest.of(0, Math.max(1, limit))).stream()
                .map(InventoryJpaAdapter::toWasteDomain)
                .toList();
    }

    @Override
    @Transactional
    public StockCount saveStockCount(StockCount sc) {
        return translator.execute("save stock count", () -> {
            Optional<StockCountEntity> existingOpt = countRepo.findByIdAndTenantIdAndBranchId(
                    sc.id(), sc.tenantId().value(), sc.branchId().value());
            StockCountEntity e;
            if (existingOpt.isPresent()) {
                e = existingOpt.get();
                if (e.getVersion() != sc.version()) {
                    throw new OptimisticLockingFailureException("StockCount version mismatch");
                }
            } else {
                e = new StockCountEntity(sc.id(), sc.tenantId().value(), sc.branchId().value(),
                        sc.countNumber(), sc.stockLocationId(), sc.categoryId(), sc.createdByUserId());
            }
            e.setStatus(sc.status());
            e.setSubmittedByUserId(sc.submittedByUserId());
            e.setApprovedByUserId(sc.approvedByUserId());
            e.setPostedByUserId(sc.postedByUserId());
            e.setApprovalId(sc.approvalId());
            e.setTotalVarianceCost(sc.totalVarianceCost());
            e.setNotes(sc.notes());
            e.setSubmittedAt(sc.submittedAt());
            e.setApprovedAt(sc.approvedAt());
            e.setPostedAt(sc.postedAt());
            StockCountEntity saved = countRepo.saveAndFlush(e);

            Map<UUID, StockCountItemEntity> existingItems = countItemRepo.findByStockCountId(saved.getId()).stream()
                    .collect(Collectors.toMap(StockCountItemEntity::getId, Function.identity()));
            for (StockCountItem ci : sc.items()) {
                StockCountItemEntity itemEnt = existingItems.getOrDefault(ci.id(),
                        new StockCountItemEntity(ci.id(), ci.tenantId().value(), saved.getId(), ci.inventoryItemId()));
                itemEnt.setSystemQuantity(ci.systemQuantity());
                itemEnt.setCountedQuantity(ci.countedQuantity());
                itemEnt.setVarianceQuantity(ci.varianceQuantity());
                itemEnt.setUnitCost(ci.unitCost());
                itemEnt.setVarianceCost(ci.varianceCost());
                itemEnt.setRecountRequired(ci.recountRequired());
                itemEnt.setOriginalCountedQuantity(ci.originalCountedQuantity());
                itemEnt.setNotes(ci.notes());
                countItemRepo.saveAndFlush(itemEnt);
            }
            return toStockCountDomain(saved, countItemRepo.findByStockCountId(saved.getId()));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockCount> findStockCountScoped(UUID countId, TenantId tenantId, BranchId branchId) {
        return countRepo.findByIdAndTenantIdAndBranchId(countId, tenantId.value(), branchId.value())
                .map(e -> toStockCountDomain(e, countItemRepo.findByStockCountId(e.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockCount> findStockCountsByBranch(TenantId tenantId, BranchId branchId) {
        return countRepo.findByTenantIdAndBranchIdOrderByCreatedAtDesc(tenantId.value(), branchId.value()).stream()
                .map(e -> toStockCountDomain(e, countItemRepo.findByStockCountId(e.getId())))
                .toList();
    }

    @Override
    @Transactional
    public StockTransfer saveStockTransfer(StockTransfer st) {
        return translator.execute("save stock transfer", () -> {
            Optional<StockTransferEntity> existingOpt = transferRepo.findByIdAndTenantId(st.id(), st.tenantId().value());
            StockTransferEntity e;
            if (existingOpt.isPresent()) {
                e = existingOpt.get();
                if (e.getVersion() != st.version()) {
                    throw new OptimisticLockingFailureException("StockTransfer version mismatch");
                }
            } else {
                e = new StockTransferEntity(st.id(), st.tenantId().value(), st.transferNumber());
                e.setSourceBranchId(st.sourceBranchId().value());
                e.setSourceLocationId(st.sourceLocationId());
                e.setDestinationBranchId(st.destinationBranchId().value());
                e.setDestinationLocationId(st.destinationLocationId());
                e.setRequestedByUserId(st.requestedByUserId());
                e.setRequestedAt(st.requestedAt());
            }
            e.setStatus(st.status());
            e.setApprovedByUserId(st.approvedByUserId());
            e.setDispatchedByUserId(st.dispatchedByUserId());
            e.setReceivedByUserId(st.receivedByUserId());
            e.setNotes(st.notes());
            e.setApprovedAt(st.approvedAt());
            e.setDispatchedAt(st.dispatchedAt());
            e.setReceivedAt(st.receivedAt());
            StockTransferEntity saved = transferRepo.saveAndFlush(e);

            Map<UUID, StockTransferItemEntity> existingItems = transferItemRepo.findByStockTransferId(saved.getId()).stream()
                    .collect(Collectors.toMap(StockTransferItemEntity::getId, Function.identity()));
            for (StockTransferItem ti : st.items()) {
                StockTransferItemEntity itemEnt = existingItems.getOrDefault(ti.id(),
                        new StockTransferItemEntity(ti.id(), ti.tenantId().value(), saved.getId(),
                                ti.inventoryItemId(), ti.unitId()));
                itemEnt.setRequestedQuantity(ti.requestedQuantity());
                itemEnt.setDispatchedQuantity(ti.dispatchedQuantity());
                itemEnt.setReceivedQuantity(ti.receivedQuantity());
                itemEnt.setDamagedQuantity(ti.damagedQuantity());
                itemEnt.setMissingQuantity(ti.missingQuantity());
                itemEnt.setUnitCost(ti.unitCost());
                itemEnt.setNotes(ti.notes());
                transferItemRepo.saveAndFlush(itemEnt);
            }
            return toTransferDomain(saved, transferItemRepo.findByStockTransferId(saved.getId()));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockTransfer> findStockTransferScoped(UUID transferId, TenantId tenantId) {
        return transferRepo.findByIdAndTenantId(transferId, tenantId.value())
                .map(e -> toTransferDomain(e, transferItemRepo.findByStockTransferId(e.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockTransfer> findStockTransfersByTenant(TenantId tenantId) {
        return transferRepo.findByTenantIdOrderByRequestedAtDesc(tenantId.value()).stream()
                .map(e -> toTransferDomain(e, transferItemRepo.findByStockTransferId(e.getId())))
                .toList();
    }

    @Override
    @Transactional
    public InventoryAlert saveAlert(InventoryAlert a) {
        return translator.execute("save inventory alert", () -> {
            Optional<InventoryAlertEntity> existingOpt = alertRepo.findByIdAndTenantIdAndBranchId(
                    a.id(), a.tenantId().value(), a.branchId().value());
            InventoryAlertEntity e = existingOpt.orElseGet(() ->
                    new InventoryAlertEntity(a.id(), a.tenantId().value(), a.branchId().value(),
                            a.inventoryItemId(), a.dedupKey()));
            if (existingOpt.isPresent() && e.getVersion() != a.version()) {
                throw new OptimisticLockingFailureException("InventoryAlert version mismatch");
            }
            e.setStockLocationId(a.stockLocationId());
            e.setAlertType(a.alertType());
            e.setSeverity(a.severity());
            e.setMessage(a.message());
            e.setCurrentValue(a.currentValue());
            e.setThresholdValue(a.thresholdValue());
            e.setStatus(a.status());
            e.setResolvedByUserId(a.resolvedByUserId());
            e.setResolvedAt(a.resolvedAt());
            return toAlertDomain(alertRepo.saveAndFlush(e));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryAlert> findAlertScoped(UUID alertId, TenantId tenantId, BranchId branchId) {
        return alertRepo.findByIdAndTenantIdAndBranchId(alertId, tenantId.value(), branchId.value())
                .map(InventoryJpaAdapter::toAlertDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryAlert> findActiveAlertByDedup(TenantId tenantId, BranchId branchId, String dedupKey) {
        return alertRepo.findByTenantIdAndBranchIdAndDedupKeyAndStatusIn(
                        tenantId.value(), branchId.value(), dedupKey, List.of("ACTIVE", "ACKNOWLEDGED"))
                .map(InventoryJpaAdapter::toAlertDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryAlert> findAlertsByBranch(TenantId tenantId, BranchId branchId, boolean activeOnly) {
        List<InventoryAlertEntity> list = activeOnly
                ? alertRepo.findByTenantIdAndBranchIdAndStatusInOrderByCreatedAtDesc(
                        tenantId.value(), branchId.value(), List.of("ACTIVE", "ACKNOWLEDGED"))
                : alertRepo.findByTenantIdAndBranchIdOrderByCreatedAtDesc(tenantId.value(), branchId.value());
        return list.stream().map(InventoryJpaAdapter::toAlertDomain).toList();
    }

    @Override
    @Transactional
    public List<OutboxWorkItem> claimPendingInventoryOutboxEvents(int batchSize, Instant now) {
        return outboxRepo.claimPendingInventoryEvents(now, batchSize).stream()
                .map(e -> {
                    e.setStatus("IN_FLIGHT");
                    outboxRepo.saveAndFlush(e);
                    return new OutboxWorkItem(
                            e.getId(), TenantId.of(e.getTenantId()),
                            e.getBranchId() != null ? BranchId.of(e.getBranchId()) : null,
                            e.getAggregateType(), e.getAggregateId(), e.getEventType(),
                            e.getIdempotencyKey(), e.getPayloadJson(), e.getStatus(),
                            e.getRetryCount(), e.getNextAttemptAt(), e.getCreatedAt()
                    );
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOutboxEventProcessed(UUID outboxEventId) {
        return processedEventRepo.existsById(outboxEventId);
    }

    @Override
    @Transactional
    public void markOutboxEventProcessed(UUID outboxEventId, TenantId tenantId, BranchId branchId, String eventType, Instant processedAt) {
        if (!processedEventRepo.existsById(outboxEventId)) {
            processedEventRepo.saveAndFlush(new InventoryProcessedEventEntity(
                    outboxEventId, tenantId.value(), branchId != null ? branchId.value() : null, eventType, processedAt
            ));
        }
    }

    @Override
    @Transactional
    public void markOutboxEventPublished(UUID outboxEventId, Instant publishedAt) {
        outboxRepo.findById(outboxEventId).ifPresent(e -> {
            e.setStatus("PUBLISHED");
            e.setPublishedAt(publishedAt);
            outboxRepo.saveAndFlush(e);
        });
    }

    @Override
    @Transactional
    public void markOutboxEventRetry(UUID outboxEventId, int nextRetryCount, Instant nextAttemptAt, String status) {
        outboxRepo.findById(outboxEventId).ifPresent(e -> {
            e.setRetryCount(nextRetryCount);
            e.setNextAttemptAt(nextAttemptAt);
            e.setStatus(status);
            outboxRepo.saveAndFlush(e);
        });
    }

    private Recipe loadFullRecipe(RecipeEntity re) {
        List<RecipeVersion> vers = recipeVersionRepo.findByRecipeIdOrderByVersionNumberAsc(re.getId()).stream()
                .map(this::loadVersionDomain)
                .toList();
        return toRecipeDomain(re, vers);
    }

    private RecipeVersion loadVersionDomain(RecipeVersionEntity rv) {
        List<RecipeIngredient> ings = recipeIngRepo.findByRecipeVersionIdOrderByDisplayOrderAsc(rv.getId()).stream()
                .map(i -> new RecipeIngredient(
                        i.getId(), TenantId.of(i.getTenantId()), i.getRecipeVersionId(),
                        i.getIngredientId(), i.getSubRecipeId(), i.getUnitId(),
                        i.getQuantity(), i.getWasteLossPct(), i.isOptionalFlag(),
                        i.getAlternativeGroup(), i.getNotes(), i.getDisplayOrder()
                ))
                .toList();
        List<RecipeModifierMapping> mods = recipeModRepo.findByRecipeVersionId(rv.getId()).stream()
                .map(m -> new RecipeModifierMapping(
                        m.getId(), TenantId.of(m.getTenantId()), m.getRecipeVersionId(),
                        m.getModifierId(), m.getActionType(), m.getIngredientId(),
                        m.getSubRecipeId(), m.getUnitId(), m.getQuantityDelta(),
                        m.getScaleFactor(), m.getNotes()
                ))
                .toList();
        return new RecipeVersion(
                rv.getId(), TenantId.of(rv.getTenantId()),
                rv.getBranchId() != null ? BranchId.of(rv.getBranchId()) : null,
                rv.getRecipeId(), rv.getVersionNumber(), rv.getStatus(),
                rv.getYieldQuantity(), rv.getYieldUnitId(), rv.getPrepLossPct(),
                rv.getPrepInstructions(), rv.getExpectedPrepMinutes(),
                rv.getTheoreticalCost(), rv.isValidated(), rv.getPublishedAt(),
                rv.getCreatedByUserId(), rv.getVersion(), ings, mods
        );
    }

    private static Recipe toRecipeDomain(RecipeEntity re, List<RecipeVersion> versions) {
        return new Recipe(
                re.getId(), TenantId.of(re.getTenantId()),
                re.getBranchId() != null ? BranchId.of(re.getBranchId()) : null,
                re.getProductId(), re.getProductVariantId(), re.getRecipeName(),
                re.isSubRecipe(), re.getActiveVersionId(), re.isValidated(), re.isActive(),
                re.getUpdatedAt(), re.getVersion(), versions
        );
    }

    private static UnitOfMeasure toUnitDomain(UnitEntity e) {
        return new UnitOfMeasure(e.getId(), TenantId.of(e.getTenantId()), e.getCode(),
                e.getName(), e.getUnitType(), e.getBaseUnitId(), e.getConversionFactor(), e.getVersion());
    }

    private static UnitConversion toConvDomain(UnitConversionEntity e) {
        return new UnitConversion(e.getId(), TenantId.of(e.getTenantId()), e.getInventoryItemId(),
                e.getFromUnitId(), e.getToUnitId(), e.getConversionFactor(), e.isActive(), e.getVersion());
    }

    private static InventoryCategory toCategoryDomain(InventoryCategoryEntity e) {
        return new InventoryCategory(e.getId(), TenantId.of(e.getTenantId()), e.getCode(),
                e.getName(), e.getDescription(), e.getDisplayOrder(), e.isActive(), e.getVersion());
    }

    private static InventoryLocation toLocationDomain(StockLocationEntity e) {
        return new InventoryLocation(e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                e.getCode(), e.getName(), e.getLocationType(), e.isDefaultLocation(), e.isActive(), e.getVersion());
    }

    private static InventoryItem toItemDomain(IngredientEntity e) {
        return new InventoryItem(
                e.getId(), TenantId.of(e.getTenantId()),
                e.getBranchId() != null ? BranchId.of(e.getBranchId()) : null,
                e.getCategoryId(), e.getDefaultLocationId(), e.getSku(), e.getName(),
                e.getDescription(), e.getItemType(), e.getBarcode(), e.getUnitId(),
                e.getMinimumStockLevel(), e.getReorderLevel(), e.getAverageCost(),
                e.getTrackingPolicy(), e.isExpiryTracking(), e.isBatchTracking(),
                e.getNegativeStockPolicy(), e.getCostingMethod(), e.isActive(), e.getVersion()
        );
    }

    private static InventoryPolicy toPolicyDomain(InventoryPolicyEntity e) {
        return new InventoryPolicy(
                e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                e.getCommitmentMode(), e.isAllowNegativeStock(), e.isLowStockAlertEnabled(),
                e.getExpiryWarningDays(), e.getHighWasteThresholdQty(),
                e.getWasteApprovalThresholdAmount(), e.getCountVarianceApprovalPct(),
                e.isTransferApprovalRequired(), e.isAllowManualConsumptionReverse(), e.getVersion()
        );
    }

    private static InventoryBalance toBalanceDomain(StockBalanceEntity e) {
        return new InventoryBalance(
                e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                e.getStockLocationId(), e.getIngredientId(), e.getQuantityOnHand(),
                e.getQuantityReserved(), e.getAverageUnitCost(), e.getLastMovementAt(),
                e.getLastCostUpdatedAt(), e.getVersion()
        );
    }

    private static StockMovement toMovementDomain(StockMovementEntity e) {
        return new StockMovement(
                e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                e.getStockLocationId(), e.getIngredientId(), e.getActorUserId(),
                StockMovementType.valueOf(e.getMovementType()), e.getDirection(),
                e.getQuantityDelta(), e.getUnitId(), e.getOriginalQuantity(),
                e.getBalanceBefore(), e.getBalanceAfter(), e.getReservedBefore(),
                e.getReservedAfter(), e.getUnitCost(), e.getTotalCost(),
                e.getReferenceType(), e.getReferenceId(), e.getReason(),
                e.getBatchNumber(), e.getExpiryDate(), e.getApprovalId(),
                e.getOutboxEventId(), e.getIdempotencyKey(), e.getOccurredAt()
        );
    }

    private static StockReservation toReservationDomain(StockReservationEntity e, List<StockReservationItemEntity> items) {
        return new StockReservation(
                e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                e.getOrderId(), e.getSourceEventId(), e.getStatus(), e.getRecipeSnapshotJson(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getVersion(),
                items.stream().map(ri -> new StockReservationItem(
                        ri.getId(), TenantId.of(ri.getTenantId()), BranchId.of(ri.getBranchId()),
                        ri.getReservationId(), ri.getOrderItemId(), ri.getKitchenTicketItemId(),
                        ri.getIngredientId(), ri.getStockLocationId(), ri.getRecipeVersionId(),
                        ri.getReservedQuantity(), ri.getConsumedQuantity(), ri.getReleasedQuantity(),
                        ri.getStatus(), ri.getIdempotencyKey(), ri.getVersion()
                )).toList()
        );
    }

    private static StockWasteRecord toWasteDomain(StockWasteRecordEntity e) {
        return new StockWasteRecord(
                e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                e.getStockLocationId(), e.getIngredientId(), e.getUnitId(),
                e.getOriginalQuantity(), e.getBaseQuantity(), e.getUnitCost(), e.getTotalCost(),
                e.getReasonCode(), e.getNotes(), e.getAttachmentRef(), e.getActorUserId(),
                e.getSupervisorApprovalId(), e.getIdempotencyKey(), e.getMovementId(), e.getOccurredAt()
        );
    }

    private static StockCount toStockCountDomain(StockCountEntity e, List<StockCountItemEntity> items) {
        return new StockCount(
                e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                e.getCountNumber(), e.getStockLocationId(), e.getCategoryId(), e.getStatus(),
                e.getCreatedByUserId(), e.getSubmittedByUserId(), e.getApprovedByUserId(),
                e.getPostedByUserId(), e.getApprovalId(), e.getTotalVarianceCost(),
                e.getNotes(), e.getSubmittedAt(), e.getApprovedAt(), e.getPostedAt(), e.getVersion(),
                items.stream().map(ci -> new StockCountItem(
                        ci.getId(), TenantId.of(ci.getTenantId()), ci.getStockCountId(),
                        ci.getIngredientId(), ci.getSystemQuantity(), ci.getCountedQuantity(),
                        ci.getVarianceQuantity(), ci.getUnitCost(), ci.getVarianceCost(),
                        ci.isRecountRequired(), ci.getOriginalCountedQuantity(), ci.getNotes(), ci.getVersion()
                )).toList()
        );
    }

    private static StockTransfer toTransferDomain(StockTransferEntity e, List<StockTransferItemEntity> items) {
        return new StockTransfer(
                e.getId(), TenantId.of(e.getTenantId()), e.getTransferNumber(),
                BranchId.of(e.getSourceBranchId()), e.getSourceLocationId(),
                BranchId.of(e.getDestinationBranchId()), e.getDestinationLocationId(),
                e.getStatus(), e.getRequestedByUserId(), e.getApprovedByUserId(),
                e.getDispatchedByUserId(), e.getReceivedByUserId(), e.getNotes(),
                e.getRequestedAt(), e.getApprovedAt(), e.getDispatchedAt(), e.getReceivedAt(), e.getVersion(),
                items.stream().map(ti -> new StockTransferItem(
                        ti.getId(), TenantId.of(ti.getTenantId()), ti.getStockTransferId(),
                        ti.getIngredientId(), ti.getUnitId(), ti.getRequestedQuantity(),
                        ti.getDispatchedQuantity(), ti.getReceivedQuantity(),
                        ti.getDamagedQuantity(), ti.getMissingQuantity(),
                        ti.getUnitCost(), ti.getNotes(), ti.getVersion()
                )).toList()
        );
    }

    private static InventoryAlert toAlertDomain(InventoryAlertEntity e) {
        return new InventoryAlert(
                e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                e.getInventoryItemId(), e.getStockLocationId(), e.getAlertType(), e.getSeverity(),
                e.getMessage(), e.getCurrentValue(), e.getThresholdValue(), e.getStatus(),
                e.getDedupKey(), e.getResolvedByUserId(), e.getResolvedAt(), e.getCreatedAt(), e.getVersion()
        );
    }
}
