package com.restaurantpos.application.inventory;

import com.restaurantpos.application.audit.AuditRecordingService;
import com.restaurantpos.application.security.PermissionPolicy;
import com.restaurantpos.domain.exception.DomainException;
import com.restaurantpos.domain.exception.ResourceNotFoundException;
import com.restaurantpos.domain.exception.UnauthorizedOperationException;
import com.restaurantpos.domain.inventory.CostingService;
import com.restaurantpos.domain.inventory.InventoryAlert;
import com.restaurantpos.domain.inventory.InventoryBalance;
import com.restaurantpos.domain.inventory.InventoryItem;
import com.restaurantpos.domain.inventory.InventoryLocation;
import com.restaurantpos.domain.inventory.InventoryPolicy;
import com.restaurantpos.domain.inventory.Recipe;
import com.restaurantpos.domain.inventory.RecipeCalculationService;
import com.restaurantpos.domain.inventory.RecipeVersion;
import com.restaurantpos.domain.inventory.StockMovement;
import com.restaurantpos.domain.inventory.StockMovementType;
import com.restaurantpos.domain.inventory.StockReservation;
import com.restaurantpos.domain.inventory.StockReservationItem;
import com.restaurantpos.domain.inventory.StockWasteRecord;
import com.restaurantpos.domain.inventory.UnitConversion;
import com.restaurantpos.domain.inventory.UnitConversionService;
import com.restaurantpos.domain.inventory.UnitOfMeasure;
import com.restaurantpos.domain.port.BranchEventPublishPort;
import com.restaurantpos.domain.port.InventoryPayloadCodecPort;
import com.restaurantpos.domain.port.InventoryRepositoryPort;
import com.restaurantpos.domain.port.SupervisorApprovalRepositoryPort;
import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.domain.security.SupervisorApproval;
import com.restaurantpos.shared.dto.inventory.InventoryBalanceDto;
import com.restaurantpos.shared.dto.inventory.InventoryValuationReportDto;
import com.restaurantpos.shared.dto.inventory.RecordStockAdjustmentRequest;
import com.restaurantpos.shared.dto.inventory.RecordWasteRequest;
import com.restaurantpos.shared.dto.inventory.StockMovementDto;
import com.restaurantpos.shared.dto.inventory.StockReservationDto;
import com.restaurantpos.shared.dto.inventory.StockWasteRecordDto;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.shared.security.AuditAction;
import com.restaurantpos.shared.security.PermissionCode;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Authoritative inventory ledger engine:
 * - Enforces append-only StockMovement writes with before/after balance & reservation snapshots
 * - Computes Weighted Average Cost (WAC) on positive receipts and adjustments
 * - Separates OnHand, Reserved, and Available (= OnHand - Reserved) with optimistic concurrency control
 * - Executes idempotent order/kitchen reservation, prep-start consumption, release, and re-fire deductions
 * - Generates deduplicated LOW_STOCK / OUT_OF_STOCK / HIGH_WASTE alerts without flooding WebSockets.
 */
public class StockLedgerService {

    private static final MathContext MC = new MathContext(18, RoundingMode.HALF_UP);

    private final InventoryRepositoryPort inventoryRepo;
    private final SupervisorApprovalRepositoryPort approvalRepo;
    private final BranchEventPublishPort eventPublisher;
    private final AuditRecordingService auditService;
    private final SystemClockPort clock;

    public StockLedgerService(
            InventoryRepositoryPort inventoryRepo,
            SupervisorApprovalRepositoryPort approvalRepo,
            BranchEventPublishPort eventPublisher,
            AuditRecordingService auditService,
            SystemClockPort clock
    ) {
        this.inventoryRepo = inventoryRepo;
        this.approvalRepo = approvalRepo;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.clock = clock;
    }

    public StockMovementDto recordAdjustment(AccessPrincipal actor, RecordStockAdjustmentRequest req, CorrelationId corr) {
        PermissionCode needed = actor.hasPermission(PermissionCode.INVENTORY_STOCK_ADJUST)
                ? PermissionCode.INVENTORY_STOCK_ADJUST : PermissionCode.INVENTORY_ADJUST;
        BranchId branchId = PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), needed);

        Optional<StockMovement> existing = inventoryRepo.findMovementByIdempotency(
                actor.tenantId(), branchId, req.idempotencyKey().trim());
        if (existing.isPresent()) {
            return toMovementDto(existing.get(), actor.tenantId(), branchId, actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW));
        }

        InventoryLocation location = inventoryRepo.findLocationScoped(req.stockLocationId(), actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock location not found in branch"));
        InventoryItem item = inventoryRepo.findItemScoped(req.inventoryItemId(), actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found"));
        List<UnitOfMeasure> units = inventoryRepo.findUnitsByTenant(actor.tenantId());
        List<UnitConversion> conversions = inventoryRepo.findUnitConversionsByTenant(actor.tenantId());

        BigDecimal baseQty = UnitConversionService.convert(
                        req.quantity(), req.unitId(), item.unitId(), item.id(), units, conversions)
                .setScale(UnitConversionService.QUANTITY_SCALE, RoundingMode.HALF_UP);
        if (baseQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("INVALID_QUANTITY", "Converted base quantity must be positive");
        }

        StockMovementType type = StockMovementType.valueOf(req.adjustmentType());
        boolean incoming = "IN".equals(type.defaultDirection());
        if (!incoming && req.allowNegativeOverride()) {
            requireValidApproval(actor.tenantId(), branchId, req.supervisorApprovalId(), PermissionCode.INVENTORY_STOCK_ADJUST);
        }

        StockMovement saved = applyBalanceMutation(
                actor.tenantId(), branchId, location.id(), item, actor.userId(), type,
                incoming ? baseQty : baseQty.negate(),
                req.unitId(), req.quantity(), req.unitCost(),
                "MANUAL_ADJUSTMENT", null, req.reason().trim(),
                req.batchNumber(), req.expiryDate(), req.supervisorApprovalId(), null,
                req.idempotencyKey().trim(), req.allowNegativeOverride()
        );

        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.STOCK_ADJUSTED,
                "InventoryItem", item.id().toString(),
                "{\"type\":\"" + type.name() + "\",\"qty\":" + saved.quantityDelta() + "}", corr);
        eventPublisher.publish(actor.tenantId(), branchId, "INVENTORY_BALANCE_UPDATED", item.id());

        return toMovementDto(saved, actor.tenantId(), branchId, actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW));
    }

    public StockWasteRecordDto recordWaste(AccessPrincipal actor, RecordWasteRequest req, CorrelationId corr) {
        PermissionCode needed = actor.hasPermission(PermissionCode.INVENTORY_STOCK_WASTE)
                ? PermissionCode.INVENTORY_STOCK_WASTE : PermissionCode.INVENTORY_ADJUST;
        BranchId branchId = PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), needed);

        String idempKey = req.idempotencyKey().trim();
        Optional<StockWasteRecord> existing = inventoryRepo.findWasteByIdempotency(actor.tenantId(), branchId, idempKey);
        if (existing.isPresent()) {
            return toWasteDto(existing.get(), actor.tenantId(), actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW));
        }

        InventoryLocation location = inventoryRepo.findLocationScoped(req.stockLocationId(), actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock location not found in branch"));
        InventoryItem item = inventoryRepo.findItemScoped(req.inventoryItemId(), actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found"));
        List<UnitOfMeasure> units = inventoryRepo.findUnitsByTenant(actor.tenantId());
        List<UnitConversion> conversions = inventoryRepo.findUnitConversionsByTenant(actor.tenantId());

        BigDecimal baseQty = UnitConversionService.convert(
                        req.quantity(), req.unitId(), item.unitId(), item.id(), units, conversions)
                .setScale(UnitConversionService.QUANTITY_SCALE, RoundingMode.HALF_UP);
        if (baseQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("INVALID_QUANTITY", "Waste quantity must be positive");
        }

        InventoryPolicy policy = getEffectivePolicy(actor.tenantId(), branchId);
        InventoryBalance currentBal = inventoryRepo.findBalanceScoped(actor.tenantId(), branchId, location.id(), item.id()).orElse(null);
        BigDecimal unitCost = effectiveUnitCost(item, currentBal);
        BigDecimal totalCost = baseQty.multiply(unitCost, MC).setScale(CostingService.COST_SCALE, RoundingMode.HALF_UP);

        if (totalCost.compareTo(policy.wasteApprovalThresholdAmount()) > 0) {
            requireValidApproval(actor.tenantId(), branchId, req.supervisorApprovalId(), PermissionCode.INVENTORY_STOCK_ADJUST);
        }

        StockMovement movement = applyBalanceMutation(
                actor.tenantId(), branchId, location.id(), item, actor.userId(), StockMovementType.WASTE,
                baseQty.negate(), req.unitId(), req.quantity(), unitCost,
                "WASTE_RECORD", null, req.reasonCode() + (req.notes() != null ? ": " + req.notes() : ""),
                null, null, req.supervisorApprovalId(), null,
                "waste-" + idempKey, policy.allowNegativeStock()
        );

        StockWasteRecord waste = inventoryRepo.saveWasteRecord(new StockWasteRecord(
                UUID.randomUUID(), actor.tenantId(), branchId, location.id(), item.id(),
                req.unitId(), req.quantity(), baseQty, unitCost, totalCost,
                req.reasonCode(), req.notes(), req.attachmentRef(), actor.userId(),
                req.supervisorApprovalId(), idempKey, movement.id(), clock.now()
        ));

        if (baseQty.compareTo(policy.highWasteThresholdQty()) >= 0) {
            ensureAlertActive(
                    actor.tenantId(), branchId, item, location.id(),
                    "HIGH_WASTE", "WARNING",
                    "High waste recorded for " + item.name() + ": " + baseQty,
                    baseQty, policy.highWasteThresholdQty(),
                    "HIGH_WASTE:" + item.id()
            );
        }

        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.STOCK_WASTE_RECORDED,
                "StockWasteRecord", waste.id().toString(),
                "{\"reason\":\"" + req.reasonCode() + "\",\"qty\":" + baseQty + "}", corr);
        eventPublisher.publish(actor.tenantId(), branchId, "INVENTORY_BALANCE_UPDATED", item.id());

        return toWasteDto(waste, actor.tenantId(), actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW));
    }

    /**
     * Triggered when an order is dispatched to kitchen (ORDER_SENT_TO_KITCHEN -> INVENTORY_RESERVE_ORDER).
     * Expands active recipe + modifiers into a frozen recipe version reference per order item.
     * Skips any order_item_id already present in stock_reservation_items so supplemental tickets never double-reserve.
     */
    public void reserveForOrder(TenantId tenantId, BranchId branchId, UUID outboxEventId, InventoryPayloadCodecPort.ReserveOrderPayload snap) {
        InventoryPolicy policy = getEffectivePolicy(tenantId, branchId);
        if ("DISABLED".equals(policy.commitmentMode())) {
            return;
        }
        boolean directConsume = "DIRECT_CONSUME_ON_SEND".equals(policy.commitmentMode());

        StockReservation existingRes = inventoryRepo.findReservationByOrder(tenantId, branchId, snap.orderId()).orElse(null);
        UUID resId = existingRes != null ? existingRes.id() : UUID.randomUUID();
        List<StockReservationItem> resItems = existingRes != null ? new ArrayList<>(existingRes.items()) : new ArrayList<>();

        Map<UUID, Recipe> allRecipes = inventoryRepo.findRecipesByTenant(tenantId).stream()
                .collect(Collectors.toMap(Recipe::id, Function.identity()));
        Map<UUID, InventoryItem> itemsMap = inventoryRepo.findItemsByTenant(tenantId).stream()
                .collect(Collectors.toMap(InventoryItem::id, Function.identity()));
        List<UnitOfMeasure> allUnits = inventoryRepo.findUnitsByTenant(tenantId);
        List<UnitConversion> conversions = inventoryRepo.findUnitConversionsByTenant(tenantId);
        InventoryLocation defaultLoc = resolveDefaultLocation(tenantId, branchId);
        if (defaultLoc == null) {
            return; // Branch has no storage locations configured yet
        }

        boolean anyCreated = false;
        for (InventoryPayloadCodecPort.OrderPrepItemSnapshot itemSnap : snap.items()) {
            boolean alreadyTracked = resItems.stream().anyMatch(ri -> ri.orderItemId().equals(itemSnap.orderItemId()));
            if (alreadyTracked || itemSnap.productId() == null) {
                continue;
            }
            Recipe recipe = findMatchingRecipe(tenantId, branchId, itemSnap.productId(), itemSnap.productVariantId(), allRecipes);
            RecipeVersion ver = RecipeCalculationService.findActiveOrLatestVersion(recipe);
            if (recipe == null || ver == null || !recipe.active() || !ver.validated()) {
                continue;
            }

            var modInputs = itemSnap.modifierIds() != null
                    ? itemSnap.modifierIds().stream().map(m -> new RecipeCalculationService.ModifierSelectionInput(m, 1)).toList()
                    : List.<RecipeCalculationService.ModifierSelectionInput>of();
            var reqs = RecipeCalculationService.expandRequirements(
                    ver, itemSnap.quantity(), modInputs,
                    allRecipes, itemsMap, Map.of(), allUnits, conversions
            );
            for (RecipeCalculationService.IngredientRequirement req : reqs) {
                InventoryItem invItem = itemsMap.get(req.inventoryItemId());
                if (invItem == null) {
                    continue;
                }
                UUID locId = invItem.defaultLocationId() != null ? invItem.defaultLocationId() : defaultLoc.id();
                String idemp = (directConsume ? "cons-dir-" : "res-") + itemSnap.orderItemId() + "-" + invItem.id();
                if (directConsume) {
                    applyBalanceMutation(
                            tenantId, branchId, locId, invItem, snap.actorUserId(), StockMovementType.SALE_CONSUMPTION,
                            req.baseQuantityRequired().negate(), invItem.unitId(), req.baseQuantityRequired(),
                            null, "ORDER_ITEM", itemSnap.orderItemId(),
                            "Direct sale consumption on dispatch for " + snap.orderNumber(),
                            null, null, null, outboxEventId, idemp, policy.allowNegativeStock()
                    );
                    resItems.add(new StockReservationItem(
                            UUID.randomUUID(), tenantId, branchId, resId, itemSnap.orderItemId(), null,
                            invItem.id(), locId, ver.id(), BigDecimal.ZERO, req.baseQuantityRequired(),
                            BigDecimal.ZERO, "CONSUMED", idemp, 0L
                    ));
                } else {
                    applyReservationMutation(
                            tenantId, branchId, locId, invItem, snap.actorUserId(),
                            req.baseQuantityRequired(), "ORDER_ITEM", itemSnap.orderItemId(),
                            "Stock reservation for order " + snap.orderNumber(), outboxEventId, idemp, policy.allowNegativeStock()
                    );
                    resItems.add(new StockReservationItem(
                            UUID.randomUUID(), tenantId, branchId, resId, itemSnap.orderItemId(), null,
                            invItem.id(), locId, ver.id(), req.baseQuantityRequired(), BigDecimal.ZERO,
                            BigDecimal.ZERO, "RESERVED", idemp, 0L
                    ));
                }
                anyCreated = true;
            }
        }

        if (anyCreated) {
            Instant now = clock.now();
            String resStatus = directConsume ? "CONSUMED" : (existingRes != null ? existingRes.status() : "ACTIVE");
            inventoryRepo.saveReservation(new StockReservation(
                    resId, tenantId, branchId, snap.orderId(), outboxEventId, resStatus,
                    existingRes != null ? existingRes.recipeSnapshotJson() : null,
                    existingRes != null ? existingRes.createdAt() : now, now,
                    existingRes != null ? existingRes.version() : 0L, resItems
            ));
            auditService.record(tenantId, branchId, snap.actorUserId(),
                    directConsume ? AuditAction.STOCK_CONSUMED : AuditAction.STOCK_RESERVED,
                    "Order", snap.orderId().toString(), null, CorrelationId.of("inv-" + outboxEventId));
        }
    }

    /**
     * Triggered when Kitchen preparation begins (INVENTORY_PREP_STARTED):
     * Converts existing RESERVED line items into CONSUMED (decreasing both onHand & reserved),
     * using the frozen recipeVersionId captured during reservation rather than latest mutable recipe.
     */
    public void consumeForPrep(TenantId tenantId, BranchId branchId, UUID outboxEventId, InventoryPayloadCodecPort.PrepStartedPayload payload) {
        InventoryPolicy policy = getEffectivePolicy(tenantId, branchId);
        if ("DISABLED".equals(policy.commitmentMode())) {
            return;
        }
        StockReservation reservation = inventoryRepo.findReservationByOrder(tenantId, branchId, payload.orderId()).orElse(null);
        Map<UUID, InventoryItem> itemsMap = inventoryRepo.findItemsByTenant(tenantId).stream()
                .collect(Collectors.toMap(InventoryItem::id, Function.identity()));
        Map<UUID, Recipe> allRecipes = inventoryRepo.findRecipesByTenant(tenantId).stream()
                .collect(Collectors.toMap(Recipe::id, Function.identity()));
        List<UnitOfMeasure> allUnits = inventoryRepo.findUnitsByTenant(tenantId);
        List<UnitConversion> conversions = inventoryRepo.findUnitConversionsByTenant(tenantId);
        InventoryLocation defaultLoc = resolveDefaultLocation(tenantId, branchId);
        if (defaultLoc == null) {
            return;
        }

        UUID resId = reservation != null ? reservation.id() : UUID.randomUUID();
        List<StockReservationItem> resItems = reservation != null ? new ArrayList<>(reservation.items()) : new ArrayList<>();
        boolean changed = false;

        for (InventoryPayloadCodecPort.OrderPrepItemSnapshot itemSnap : payload.items()) {
            List<StockReservationItem> matching = resItems.stream()
                    .filter(ri -> ri.orderItemId().equals(itemSnap.orderItemId()))
                    .toList();

            if (!matching.isEmpty()) {
                for (int idx = 0; idx < resItems.size(); idx++) {
                    StockReservationItem ri = resItems.get(idx);
                    if (!ri.orderItemId().equals(itemSnap.orderItemId()) || !"RESERVED".equals(ri.status())) {
                        continue;
                    }
                    InventoryItem invItem = itemsMap.get(ri.inventoryItemId());
                    if (invItem == null) {
                        continue;
                    }
                    String consIdemp = "cons-" + ri.orderItemId() + "-" + ri.inventoryItemId();
                    consumeFromReservationMutation(
                            tenantId, branchId, ri.stockLocationId(), invItem, payload.actorUserId(),
                            ri.reservedQuantity(), "ORDER_ITEM", ri.orderItemId(),
                            "Prep consumption for order item " + ri.orderItemId(),
                            outboxEventId, consIdemp, policy.allowNegativeStock()
                    );
                    resItems.set(idx, new StockReservationItem(
                            ri.id(), ri.tenantId(), ri.branchId(), ri.reservationId(), ri.orderItemId(),
                            itemSnap.kitchenTicketItemId(), ri.inventoryItemId(), ri.stockLocationId(),
                            ri.recipeVersionId(), BigDecimal.ZERO, ri.reservedQuantity(),
                            ri.releasedQuantity(), "CONSUMED", ri.idempotencyKey(), ri.version()
                    ));
                    changed = true;
                }
            } else if (itemSnap.productId() != null) {
                Recipe recipe = findMatchingRecipe(tenantId, branchId, itemSnap.productId(), itemSnap.productVariantId(), allRecipes);
                RecipeVersion ver = RecipeCalculationService.findActiveOrLatestVersion(recipe);
                if (recipe != null && ver != null && recipe.active() && ver.validated()) {
                    var modInputs = itemSnap.modifierIds() != null
                            ? itemSnap.modifierIds().stream().map(m -> new RecipeCalculationService.ModifierSelectionInput(m, 1)).toList()
                            : List.<RecipeCalculationService.ModifierSelectionInput>of();
                    var reqs = RecipeCalculationService.expandRequirements(
                            ver, itemSnap.quantity(), modInputs,
                            allRecipes, itemsMap, Map.of(), allUnits, conversions
                    );
                    for (RecipeCalculationService.IngredientRequirement req : reqs) {
                        InventoryItem invItem = itemsMap.get(req.inventoryItemId());
                        if (invItem == null) continue;
                        UUID locId = invItem.defaultLocationId() != null ? invItem.defaultLocationId() : defaultLoc.id();
                        String idemp = "cons-" + itemSnap.orderItemId() + "-" + invItem.id();
                        applyBalanceMutation(
                                tenantId, branchId, locId, invItem, payload.actorUserId(), StockMovementType.SALE_CONSUMPTION,
                                req.baseQuantityRequired().negate(), invItem.unitId(), req.baseQuantityRequired(),
                                null, "ORDER_ITEM", itemSnap.orderItemId(),
                                "Prep consumption on kitchen start",
                                null, null, null, outboxEventId, idemp, policy.allowNegativeStock()
                        );
                        resItems.add(new StockReservationItem(
                                UUID.randomUUID(), tenantId, branchId, resId, itemSnap.orderItemId(),
                                itemSnap.kitchenTicketItemId(), invItem.id(), locId, ver.id(),
                                BigDecimal.ZERO, req.baseQuantityRequired(), BigDecimal.ZERO, "CONSUMED", idemp, 0L
                        ));
                        changed = true;
                    }
                }
            }
        }

        if (changed) {
            boolean allDone = resItems.stream().noneMatch(ri -> "RESERVED".equals(ri.status()));
            String nextStatus = allDone ? "CONSUMED" : "PARTIALLY_CONSUMED";
            Instant now = clock.now();
            inventoryRepo.saveReservation(new StockReservation(
                    resId, tenantId, branchId, payload.orderId(),
                    reservation != null ? reservation.sourceEventId() : outboxEventId,
                    nextStatus, reservation != null ? reservation.recipeSnapshotJson() : null,
                    reservation != null ? reservation.createdAt() : now, now,
                    reservation != null ? reservation.version() : 0L, resItems
            ));
            auditService.record(tenantId, branchId, payload.actorUserId(), AuditAction.STOCK_CONSUMED,
                    "Order", payload.orderId().toString(), null, CorrelationId.of("inv-" + outboxEventId));
        }
    }

    /**
     * Releases any still-RESERVED items upon order cancellation BEFORE preparation.
     * Already CONSUMED items are intentionally NOT restocked automatically (explicit policy rule).
     */
    public void releaseOrderReservation(TenantId tenantId, BranchId branchId, UUID outboxEventId, UUID orderId, String reason) {
        StockReservation reservation = inventoryRepo.findReservationByOrder(tenantId, branchId, orderId).orElse(null);
        if (reservation == null) {
            return;
        }
        Map<UUID, InventoryItem> itemsMap = inventoryRepo.findItemsByTenant(tenantId).stream()
                .collect(Collectors.toMap(InventoryItem::id, Function.identity()));
        List<StockReservationItem> updated = new ArrayList<>();
        boolean anyReleased = false;
        for (StockReservationItem ri : reservation.items()) {
            if ("RESERVED".equals(ri.status()) && ri.reservedQuantity().compareTo(BigDecimal.ZERO) > 0) {
                InventoryItem item = itemsMap.get(ri.inventoryItemId());
                if (item != null) {
                    String relIdemp = "rel-" + ri.orderItemId() + "-" + ri.inventoryItemId();
                    releaseReservationMutation(
                            tenantId, branchId, ri.stockLocationId(), item, null,
                            ri.reservedQuantity(), "ORDER_CANCEL", orderId,
                            "Release reservation: " + reason, outboxEventId, relIdemp
                    );
                }
                updated.add(new StockReservationItem(
                        ri.id(), ri.tenantId(), ri.branchId(), ri.reservationId(), ri.orderItemId(),
                        ri.kitchenTicketItemId(), ri.inventoryItemId(), ri.stockLocationId(), ri.recipeVersionId(),
                        BigDecimal.ZERO, ri.consumedQuantity(), ri.reservedQuantity(),
                        "RELEASED", ri.idempotencyKey(), ri.version()
                ));
                anyReleased = true;
            } else {
                updated.add(ri);
            }
        }
        if (anyReleased) {
            boolean anyConsumed = updated.stream().anyMatch(ri -> "CONSUMED".equals(ri.status()));
            Instant now = clock.now();
            inventoryRepo.saveReservation(new StockReservation(
                    reservation.id(), tenantId, branchId, orderId, reservation.sourceEventId(),
                    anyConsumed ? "PARTIALLY_CONSUMED" : "RELEASED",
                    reservation.recipeSnapshotJson(), reservation.createdAt(), now,
                    reservation.version(), updated
            ));
            auditService.record(tenantId, branchId, null, AuditAction.STOCK_RESERVATION_RELEASED,
                    "Order", orderId.toString(), null, CorrelationId.of("inv-" + outboxEventId));
        }
    }

    /**
     * Explicit re-fire deduction (INVENTORY_TICKET_REFIRED): deducts new kitchen ingredients cleanly
     * using idempotency key tied to `refireTicketId` so original order consumption is never overwritten.
     */
    public void consumeForRefire(TenantId tenantId, BranchId branchId, UUID outboxEventId, InventoryPayloadCodecPort.TicketRefirePayload payload) {
        InventoryPolicy policy = getEffectivePolicy(tenantId, branchId);
        if ("DISABLED".equals(policy.commitmentMode())) {
            return;
        }
        Map<UUID, Recipe> allRecipes = inventoryRepo.findRecipesByTenant(tenantId).stream()
                .collect(Collectors.toMap(Recipe::id, Function.identity()));
        Map<UUID, InventoryItem> itemsMap = inventoryRepo.findItemsByTenant(tenantId).stream()
                .collect(Collectors.toMap(InventoryItem::id, Function.identity()));
        List<UnitOfMeasure> allUnits = inventoryRepo.findUnitsByTenant(tenantId);
        List<UnitConversion> conversions = inventoryRepo.findUnitConversionsByTenant(tenantId);
        InventoryLocation defaultLoc = resolveDefaultLocation(tenantId, branchId);
        if (defaultLoc == null) {
            return;
        }

        for (InventoryPayloadCodecPort.OrderPrepItemSnapshot itemSnap : payload.items()) {
            if (itemSnap.productId() == null) continue;
            Recipe recipe = findMatchingRecipe(tenantId, branchId, itemSnap.productId(), itemSnap.productVariantId(), allRecipes);
            RecipeVersion ver = RecipeCalculationService.findActiveOrLatestVersion(recipe);
            if (recipe == null || ver == null || !recipe.active() || !ver.validated()) continue;

            var modInputs = itemSnap.modifierIds() != null
                    ? itemSnap.modifierIds().stream().map(m -> new RecipeCalculationService.ModifierSelectionInput(m, 1)).toList()
                    : List.<RecipeCalculationService.ModifierSelectionInput>of();
            var reqs = RecipeCalculationService.expandRequirements(
                    ver, itemSnap.quantity(), modInputs, allRecipes, itemsMap, Map.of(), allUnits, conversions);

            for (RecipeCalculationService.IngredientRequirement req : reqs) {
                InventoryItem item = itemsMap.get(req.inventoryItemId());
                if (item == null) continue;
                UUID locId = item.defaultLocationId() != null ? item.defaultLocationId() : defaultLoc.id();
                String idemp = "refire-" + payload.refireTicketId() + "-" + itemSnap.orderItemId() + "-" + item.id();
                applyBalanceMutation(
                        tenantId, branchId, locId, item, payload.actorUserId(), StockMovementType.SALE_CONSUMPTION,
                        req.baseQuantityRequired().negate(), item.unitId(), req.baseQuantityRequired(),
                        null, "KITCHEN_REFIRE", payload.refireTicketId(),
                        "Re-fire consumption: " + payload.reason(),
                        null, null, null, outboxEventId, idemp, policy.allowNegativeStock()
                );
            }
        }
        auditService.record(tenantId, branchId, payload.actorUserId(), AuditAction.STOCK_CONSUMED,
                "KitchenTicket", payload.refireTicketId().toString(),
                "{\"refireReason\":\"" + payload.reason() + "\"}", CorrelationId.of("inv-" + outboxEventId));
    }

    public StockMovement postTransferOutMovement(
            TenantId tenantId, BranchId sourceBranchId, UUID sourceLocationId,
            InventoryItem item, UUID actorUserId, BigDecimal baseQty, UUID inputUnitId,
            BigDecimal originalQty, UUID transferId, String reason, String idempotencyKey
    ) {
        return applyBalanceMutation(
                tenantId, sourceBranchId, sourceLocationId, item, actorUserId,
                StockMovementType.TRANSFER_OUT, baseQty.negate(), inputUnitId, originalQty,
                null, "STOCK_TRANSFER", transferId, reason, null, null, null, null,
                idempotencyKey, false
        );
    }

    public StockMovement postTransferInMovement(
            TenantId tenantId, BranchId destBranchId, UUID destLocationId,
            InventoryItem item, UUID actorUserId, BigDecimal baseQty, UUID inputUnitId,
            BigDecimal originalQty, BigDecimal unitCost, UUID transferId, String reason, String idempotencyKey
    ) {
        return applyBalanceMutation(
                tenantId, destBranchId, destLocationId, item, actorUserId,
                StockMovementType.TRANSFER_IN, baseQty, inputUnitId, originalQty,
                unitCost, "STOCK_TRANSFER", transferId, reason, null, null, null, null,
                idempotencyKey, true
        );
    }

    public StockMovement applyBalanceMutation(
            TenantId tenantId, BranchId branchId, UUID stockLocationId, InventoryItem item,
            UUID actorUserId, StockMovementType type, BigDecimal signedDelta,
            UUID inputUnitId, BigDecimal originalQty, BigDecimal explicitUnitCost,
            String refType, UUID refId, String reason, String batchNumber, String expiryDate,
            UUID approvalId, UUID outboxEventId, String idempotencyKey, boolean allowNegativeOverride
    ) {
        Optional<StockMovement> existing = inventoryRepo.findMovementByIdempotency(tenantId, branchId, idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        InventoryPolicy policy = getEffectivePolicy(tenantId, branchId);
        InventoryBalance bal = getOrInitBalance(tenantId, branchId, stockLocationId, item);
        BigDecimal beforeOnHand = bal.onHandQuantity();
        BigDecimal beforeReserved = bal.reservedQuantity();
        BigDecimal afterOnHand = beforeOnHand.add(signedDelta).setScale(UnitConversionService.QUANTITY_SCALE, RoundingMode.HALF_UP);
        BigDecimal afterAvailable = afterOnHand.subtract(beforeReserved);

        if (signedDelta.compareTo(BigDecimal.ZERO) < 0 && afterAvailable.compareTo(BigDecimal.ZERO) < 0) {
            enforceNegativePolicy(item, policy, allowNegativeOverride, afterAvailable);
        }

        BigDecimal prevCost = effectiveUnitCost(item, bal);
        BigDecimal txUnitCost;
        BigDecimal nextAvgCost;
        if (signedDelta.compareTo(BigDecimal.ZERO) > 0 && explicitUnitCost != null && explicitUnitCost.compareTo(BigDecimal.ZERO) >= 0) {
            txUnitCost = explicitUnitCost.setScale(CostingService.COST_SCALE, RoundingMode.HALF_UP);
            nextAvgCost = CostingService.computeWeightedAverageCost(beforeOnHand, prevCost, signedDelta, txUnitCost);
        } else {
            txUnitCost = prevCost;
            nextAvgCost = prevCost;
        }
        BigDecimal totalCost = signedDelta.abs().multiply(txUnitCost, MC).setScale(CostingService.COST_SCALE, RoundingMode.HALF_UP);
        Instant now = clock.now();

        InventoryBalance savedBal = inventoryRepo.saveBalance(new InventoryBalance(
                bal.id(), tenantId, branchId, stockLocationId, item.id(),
                afterOnHand, beforeReserved, nextAvgCost, now, now, bal.version()
        ));

        if (nextAvgCost.compareTo(item.averageCost()) != 0) {
            inventoryRepo.saveItem(new InventoryItem(
                    item.id(), item.tenantId(), item.branchId(), item.categoryId(), item.defaultLocationId(),
                    item.sku(), item.name(), item.description(), item.itemType(), item.barcode(), item.unitId(),
                    item.minimumStockLevel(), item.reorderLevel(), nextAvgCost, item.trackingPolicy(),
                    item.expiryTracking(), item.batchTracking(), item.negativeStockPolicy(),
                    item.costingMethod(), item.active(), item.version()
            ));
        }

        StockMovement movement = inventoryRepo.appendMovement(new StockMovement(
                UUID.randomUUID(), tenantId, branchId, stockLocationId, item.id(), actorUserId,
                type, signedDelta.compareTo(BigDecimal.ZERO) >= 0 ? "IN" : "OUT",
                signedDelta, inputUnitId, originalQty,
                beforeOnHand, afterOnHand, beforeReserved, beforeReserved,
                txUnitCost, totalCost, refType, refId, reason, batchNumber, expiryDate,
                approvalId, outboxEventId, idempotencyKey, now
        ));

        evaluateStockAlerts(tenantId, branchId, item, savedBal, policy);
        return movement;
    }

    private void applyReservationMutation(
            TenantId tenantId, BranchId branchId, UUID stockLocationId, InventoryItem item,
            UUID actorUserId, BigDecimal reserveQty, String refType, UUID refId,
            String reason, UUID outboxEventId, String idempotencyKey, boolean policyAllowNeg
    ) {
        if (inventoryRepo.findMovementByIdempotency(tenantId, branchId, idempotencyKey).isPresent()) {
            return;
        }
        InventoryPolicy policy = getEffectivePolicy(tenantId, branchId);
        InventoryBalance bal = getOrInitBalance(tenantId, branchId, stockLocationId, item);
        BigDecimal beforeOnHand = bal.onHandQuantity();
        BigDecimal beforeReserved = bal.reservedQuantity();
        BigDecimal afterReserved = beforeReserved.add(reserveQty).setScale(UnitConversionService.QUANTITY_SCALE, RoundingMode.HALF_UP);
        BigDecimal afterAvailable = beforeOnHand.subtract(afterReserved);

        if (afterAvailable.compareTo(BigDecimal.ZERO) < 0) {
            enforceNegativePolicy(item, policy, policyAllowNeg, afterAvailable);
        }

        BigDecimal unitCost = effectiveUnitCost(item, bal);
        Instant now = clock.now();
        InventoryBalance savedBal = inventoryRepo.saveBalance(new InventoryBalance(
                bal.id(), tenantId, branchId, stockLocationId, item.id(),
                beforeOnHand, afterReserved, unitCost, now, bal.lastCostUpdatedAt(), bal.version()
        ));
        inventoryRepo.appendMovement(new StockMovement(
                UUID.randomUUID(), tenantId, branchId, stockLocationId, item.id(), actorUserId,
                StockMovementType.RESERVATION, "RESERVE", reserveQty.negate(), item.unitId(), reserveQty,
                beforeOnHand, beforeOnHand, beforeReserved, afterReserved,
                unitCost, reserveQty.multiply(unitCost, MC).setScale(CostingService.COST_SCALE, RoundingMode.HALF_UP),
                refType, refId, reason, null, null, null, outboxEventId, idempotencyKey, now
        ));
        evaluateStockAlerts(tenantId, branchId, item, savedBal, policy);
    }

    private void consumeFromReservationMutation(
            TenantId tenantId, BranchId branchId, UUID stockLocationId, InventoryItem item,
            UUID actorUserId, BigDecimal consumeQty, String refType, UUID refId,
            String reason, UUID outboxEventId, String idempotencyKey, boolean policyAllowNeg
    ) {
        if (inventoryRepo.findMovementByIdempotency(tenantId, branchId, idempotencyKey).isPresent()) {
            return;
        }
        InventoryPolicy policy = getEffectivePolicy(tenantId, branchId);
        InventoryBalance bal = getOrInitBalance(tenantId, branchId, stockLocationId, item);
        BigDecimal beforeOnHand = bal.onHandQuantity();
        BigDecimal beforeReserved = bal.reservedQuantity();
        BigDecimal releasePart = beforeReserved.min(consumeQty);
        BigDecimal afterReserved = beforeReserved.subtract(releasePart).setScale(UnitConversionService.QUANTITY_SCALE, RoundingMode.HALF_UP);
        BigDecimal afterOnHand = beforeOnHand.subtract(consumeQty).setScale(UnitConversionService.QUANTITY_SCALE, RoundingMode.HALF_UP);
        BigDecimal unitCost = effectiveUnitCost(item, bal);
        Instant now = clock.now();

        InventoryBalance savedBal = inventoryRepo.saveBalance(new InventoryBalance(
                bal.id(), tenantId, branchId, stockLocationId, item.id(),
                afterOnHand, afterReserved, unitCost, now, bal.lastCostUpdatedAt(), bal.version()
        ));
        inventoryRepo.appendMovement(new StockMovement(
                UUID.randomUUID(), tenantId, branchId, stockLocationId, item.id(), actorUserId,
                StockMovementType.SALE_CONSUMPTION, "OUT", consumeQty.negate(), item.unitId(), consumeQty,
                beforeOnHand, afterOnHand, beforeReserved, afterReserved,
                unitCost, consumeQty.multiply(unitCost, MC).setScale(CostingService.COST_SCALE, RoundingMode.HALF_UP),
                refType, refId, reason, null, null, null, outboxEventId, idempotencyKey, now
        ));
        evaluateStockAlerts(tenantId, branchId, item, savedBal, policy);
    }

    private void releaseReservationMutation(
            TenantId tenantId, BranchId branchId, UUID stockLocationId, InventoryItem item,
            UUID actorUserId, BigDecimal releaseQty, String refType, UUID refId,
            String reason, UUID outboxEventId, String idempotencyKey
    ) {
        if (inventoryRepo.findMovementByIdempotency(tenantId, branchId, idempotencyKey).isPresent()) {
            return;
        }
        InventoryBalance bal = getOrInitBalance(tenantId, branchId, stockLocationId, item);
        BigDecimal beforeReserved = bal.reservedQuantity();
        BigDecimal actualRelease = beforeReserved.min(releaseQty);
        if (actualRelease.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal afterReserved = beforeReserved.subtract(actualRelease).setScale(UnitConversionService.QUANTITY_SCALE, RoundingMode.HALF_UP);
        BigDecimal unitCost = effectiveUnitCost(item, bal);
        Instant now = clock.now();

        inventoryRepo.saveBalance(new InventoryBalance(
                bal.id(), tenantId, branchId, stockLocationId, item.id(),
                bal.onHandQuantity(), afterReserved, unitCost, now, bal.lastCostUpdatedAt(), bal.version()
        ));
        inventoryRepo.appendMovement(new StockMovement(
                UUID.randomUUID(), tenantId, branchId, stockLocationId, item.id(), actorUserId,
                StockMovementType.RESERVATION_RELEASE, "RELEASE", actualRelease, item.unitId(), actualRelease,
                bal.onHandQuantity(), bal.onHandQuantity(), beforeReserved, afterReserved,
                unitCost, actualRelease.multiply(unitCost, MC).setScale(CostingService.COST_SCALE, RoundingMode.HALF_UP),
                refType, refId, reason, null, null, null, outboxEventId, idempotencyKey, now
        ));
    }

    public List<InventoryBalanceDto> listBalances(AccessPrincipal actor) {
        BranchId branchId = requireViewPermission(actor);
        boolean canViewCost = actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW);
        Map<UUID, InventoryItem> items = inventoryRepo.findItemsByTenant(actor.tenantId()).stream()
                .collect(Collectors.toMap(InventoryItem::id, Function.identity()));
        Map<UUID, InventoryLocation> locs = inventoryRepo.findLocationsByBranch(actor.tenantId(), branchId).stream()
                .collect(Collectors.toMap(InventoryLocation::id, Function.identity()));
        Map<UUID, UnitOfMeasure> units = inventoryRepo.findUnitsByTenant(actor.tenantId()).stream()
                .collect(Collectors.toMap(UnitOfMeasure::id, Function.identity()));

        return inventoryRepo.findBalancesByBranch(actor.tenantId(), branchId).stream()
                .map(b -> {
                    InventoryItem item = items.get(b.inventoryItemId());
                    InventoryLocation loc = locs.get(b.stockLocationId());
                    UnitOfMeasure u = item != null ? units.get(item.unitId()) : null;
                    BigDecimal avail = b.availableQuantity();
                    boolean low = item != null && item.reorderLevel() != null && avail.compareTo(item.reorderLevel()) <= 0;
                    BigDecimal cost = effectiveUnitCost(item, b);
                    BigDecimal val = b.onHandQuantity().multiply(cost, MC).setScale(CostingService.COST_SCALE, RoundingMode.HALF_UP);
                    return new InventoryBalanceDto(
                            b.id(), b.branchId().value(), b.stockLocationId(),
                            loc != null ? loc.code() : null, loc != null ? loc.name() : null,
                            b.inventoryItemId(), item != null ? item.sku() : null, item != null ? item.name() : null,
                            u != null ? u.code() : "?",
                            b.onHandQuantity(), b.reservedQuantity(), avail,
                            item != null ? item.reorderLevel() : BigDecimal.ZERO,
                            canViewCost ? cost : null,
                            canViewCost ? val : null,
                            low, b.lastMovementAt(), b.version()
                    );
                })
                .toList();
    }

    public List<StockMovementDto> listMovements(AccessPrincipal actor, UUID itemId, int limit) {
        BranchId branchId = requireViewPermission(actor);
        boolean canViewCost = actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW);
        return inventoryRepo.findMovementsByBranch(actor.tenantId(), branchId, itemId, limit).stream()
                .map(m -> toMovementDto(m, actor.tenantId(), branchId, canViewCost))
                .toList();
    }

    public List<StockReservationDto> listReservations(AccessPrincipal actor) {
        BranchId branchId = requireViewPermission(actor);
        Map<UUID, InventoryItem> items = inventoryRepo.findItemsByTenant(actor.tenantId()).stream()
                .collect(Collectors.toMap(InventoryItem::id, Function.identity()));
        return inventoryRepo.findActiveReservationsByBranch(actor.tenantId(), branchId).stream()
                .map(r -> new StockReservationDto(
                        r.id(), r.branchId().value(), r.orderId(), r.sourceEventId(), r.status(),
                        r.createdAt(), r.updatedAt(), r.version(),
                        r.items().stream().map(ri -> {
                            InventoryItem item = items.get(ri.inventoryItemId());
                            return new StockReservationDto.StockReservationItemDto(
                                    ri.id(), ri.orderItemId(), ri.kitchenTicketItemId(), ri.inventoryItemId(),
                                    item != null ? item.sku() : null, item != null ? item.name() : null,
                                    ri.stockLocationId(), ri.recipeVersionId(), ri.reservedQuantity(),
                                    ri.consumedQuantity(), ri.releasedQuantity(), ri.status(), ri.version()
                            );
                        }).toList()
                ))
                .toList();
    }

    public List<StockWasteRecordDto> listWasteRecords(AccessPrincipal actor, int limit) {
        BranchId branchId = requireViewPermission(actor);
        boolean canViewCost = actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW);
        return inventoryRepo.findWasteByBranch(actor.tenantId(), branchId, limit).stream()
                .map(w -> toWasteDto(w, actor.tenantId(), canViewCost))
                .toList();
    }

    public InventoryValuationReportDto buildValuationReport(AccessPrincipal actor, boolean lowStockOnly) {
        PermissionCode needed = lowStockOnly && !actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW)
                ? (actor.hasPermission(PermissionCode.INVENTORY_REPORT_VIEW) ? PermissionCode.INVENTORY_REPORT_VIEW : PermissionCode.INVENTORY_VIEW)
                : PermissionCode.INVENTORY_COST_VIEW;
        BranchId branchId = PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), needed);
        boolean includeCosts = actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW);

        Map<UUID, InventoryItem> items = inventoryRepo.findItemsByTenant(actor.tenantId()).stream()
                .filter(InventoryItem::active)
                .collect(Collectors.toMap(InventoryItem::id, Function.identity()));
        Map<UUID, UnitOfMeasure> units = inventoryRepo.findUnitsByTenant(actor.tenantId()).stream()
                .collect(Collectors.toMap(UnitOfMeasure::id, Function.identity()));

        Map<UUID, BigDecimal> onHandMap = new LinkedHashMap<>();
        Map<UUID, BigDecimal> reservedMap = new LinkedHashMap<>();
        Map<UUID, BigDecimal> costMap = new LinkedHashMap<>();

        for (InventoryBalance b : inventoryRepo.findBalancesByBranch(actor.tenantId(), branchId)) {
            onHandMap.merge(b.inventoryItemId(), b.onHandQuantity(), BigDecimal::add);
            reservedMap.merge(b.inventoryItemId(), b.reservedQuantity(), BigDecimal::add);
            if (b.averageUnitCost() != null && b.averageUnitCost().compareTo(BigDecimal.ZERO) > 0) {
                costMap.put(b.inventoryItemId(), b.averageUnitCost());
            }
        }

        BigDecimal totalOnHandVal = BigDecimal.ZERO;
        BigDecimal totalReservedVal = BigDecimal.ZERO;
        int lowCount = 0;
        int outCount = 0;
        List<InventoryValuationReportDto.ValuationLine> lines = new ArrayList<>();

        for (InventoryItem item : items.values()) {
            if (item.branchId() != null && !item.branchId().equals(branchId)) {
                continue;
            }
            BigDecimal oh = onHandMap.getOrDefault(item.id(), BigDecimal.ZERO);
            BigDecimal res = reservedMap.getOrDefault(item.id(), BigDecimal.ZERO);
            BigDecimal avail = oh.subtract(res);
            BigDecimal avgCost = costMap.getOrDefault(item.id(), item.averageCost() != null ? item.averageCost() : BigDecimal.ZERO);
            BigDecimal value = oh.multiply(avgCost, MC).setScale(CostingService.COST_SCALE, RoundingMode.HALF_UP);
            totalOnHandVal = totalOnHandVal.add(value);
            totalReservedVal = totalReservedVal.add(res.multiply(avgCost, MC).setScale(CostingService.COST_SCALE, RoundingMode.HALF_UP));

            boolean low = item.reorderLevel() != null && avail.compareTo(item.reorderLevel()) <= 0;
            if (avail.compareTo(BigDecimal.ZERO) <= 0) {
                outCount++;
            } else if (low) {
                lowCount++;
            }
            if (lowStockOnly && !low && avail.compareTo(BigDecimal.ZERO) > 0) {
                continue;
            }
            UnitOfMeasure u = units.get(item.unitId());
            lines.add(new InventoryValuationReportDto.ValuationLine(
                    item.id(), item.sku(), item.name(), u != null ? u.code() : "?",
                    oh, res, avail, item.reorderLevel(),
                    includeCosts ? avgCost : null,
                    includeCosts ? value : null,
                    low
            ));
        }

        BigDecimal recentWaste = inventoryRepo.findWasteByBranch(actor.tenantId(), branchId, 200).stream()
                .map(StockWasteRecord::totalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(CostingService.COST_SCALE, RoundingMode.HALF_UP);

        return new InventoryValuationReportDto(
                branchId.value(),
                clock.now(),
                "WEIGHTED_AVERAGE",
                includeCosts ? totalOnHandVal.setScale(CostingService.COST_SCALE, RoundingMode.HALF_UP) : null,
                includeCosts ? totalReservedVal.setScale(CostingService.COST_SCALE, RoundingMode.HALF_UP) : null,
                includeCosts ? recentWaste : null,
                items.size(),
                lowCount,
                outCount,
                lines
        );
    }

    public InventoryPolicy getEffectivePolicy(TenantId tenantId, BranchId branchId) {
        return inventoryRepo.findPolicy(tenantId, branchId)
                .orElseGet(() -> InventoryPolicy.defaults(UUID.randomUUID(), tenantId, branchId));
    }

    public void evaluateStockAlerts(TenantId tenantId, BranchId branchId, InventoryItem item, InventoryBalance bal, InventoryPolicy policy) {
        if (!policy.lowStockAlertEnabled()) {
            return;
        }
        BigDecimal avail = bal.availableQuantity();
        if (avail.compareTo(BigDecimal.ZERO) <= 0) {
            ensureAlertActive(tenantId, branchId, item, bal.stockLocationId(),
                    "OUT_OF_STOCK", "CRITICAL",
                    item.name() + " is out of stock (available: " + avail + ")",
                    avail, BigDecimal.ZERO, "OUT_OF_STOCK:" + item.id() + ":" + bal.stockLocationId());
        } else if (item.reorderLevel() != null && avail.compareTo(item.reorderLevel()) <= 0) {
            ensureAlertActive(tenantId, branchId, item, bal.stockLocationId(),
                    "LOW_STOCK", "WARNING",
                    item.name() + " below reorder threshold (" + avail + " <= " + item.reorderLevel() + ")",
                    avail, item.reorderLevel(), "LOW_STOCK:" + item.id() + ":" + bal.stockLocationId());
        }
    }

    public void ensureAlertActive(
            TenantId tenantId, BranchId branchId, InventoryItem item, UUID locationId,
            String alertType, String severity, String message, BigDecimal currentVal, BigDecimal threshold, String dedupKey
    ) {
        if (inventoryRepo.findActiveAlertByDedup(tenantId, branchId, dedupKey).isPresent()) {
            return; // Deduplicate active alerts without flooding
        }
        InventoryAlert saved = inventoryRepo.saveAlert(new InventoryAlert(
                UUID.randomUUID(), tenantId, branchId, item.id(), locationId,
                alertType, severity, message, currentVal, threshold,
                "ACTIVE", dedupKey, null, null, clock.now(), 0L
        ));
        eventPublisher.publish(tenantId, branchId, "INVENTORY_ALERT_CREATED", saved.id());
    }

    private void enforceNegativePolicy(InventoryItem item, InventoryPolicy policy, boolean explicitOverride, BigDecimal resultingAvail) {
        boolean allow = switch (item.negativeStockPolicy() != null ? item.negativeStockPolicy() : "INHERIT") {
            case "ALLOW" -> true;
            case "DENY" -> false;
            case "SUPERVISOR_OVERRIDE" -> explicitOverride;
            default -> policy.allowNegativeStock() || explicitOverride;
        };
        if (!allow) {
            throw new DomainException(
                    "INSUFFICIENT_STOCK",
                    "Insufficient available stock for '" + item.name() + "' (" + item.sku()
                            + "); resulting available quantity would be " + resultingAvail
            );
        }
    }

    private InventoryBalance getOrInitBalance(TenantId tenantId, BranchId branchId, UUID locationId, InventoryItem item) {
        return inventoryRepo.findBalanceScoped(tenantId, branchId, locationId, item.id())
                .orElseGet(() -> new InventoryBalance(
                        UUID.randomUUID(), tenantId, branchId, locationId, item.id(),
                        BigDecimal.ZERO.setScale(UnitConversionService.QUANTITY_SCALE, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(UnitConversionService.QUANTITY_SCALE, RoundingMode.HALF_UP),
                        item.averageCost() != null ? item.averageCost() : BigDecimal.ZERO,
                        null, null, 0L
                ));
    }

    private InventoryLocation resolveDefaultLocation(TenantId tenantId, BranchId branchId) {
        List<InventoryLocation> locs = inventoryRepo.findLocationsByBranch(tenantId, branchId);
        return locs.stream().filter(l -> l.active() && l.defaultLocation()).findFirst()
                .orElseGet(() -> locs.stream().filter(InventoryLocation::active).findFirst().orElse(null));
    }

    private Recipe findMatchingRecipe(TenantId tenantId, BranchId branchId, UUID productId, UUID variantId, Map<UUID, Recipe> allRecipes) {
        // 1. Branch + Variant
        if (variantId != null) {
            Optional<Recipe> bv = allRecipes.values().stream()
                    .filter(r -> r.active() && productId.equals(r.productId()) && variantId.equals(r.productVariantId()) && branchId.equals(r.branchId()))
                    .findFirst();
            if (bv.isPresent()) return bv.get();
        }
        // 2. Branch + Product
        Optional<Recipe> bp = allRecipes.values().stream()
                .filter(r -> r.active() && productId.equals(r.productId()) && r.productVariantId() == null && branchId.equals(r.branchId()))
                .findFirst();
        if (bp.isPresent()) return bp.get();
        // 3. Tenant + Variant
        if (variantId != null) {
            Optional<Recipe> tv = allRecipes.values().stream()
                    .filter(r -> r.active() && productId.equals(r.productId()) && variantId.equals(r.productVariantId()) && r.branchId() == null)
                    .findFirst();
            if (tv.isPresent()) return tv.get();
        }
        // 4. Tenant + Product
        return allRecipes.values().stream()
                .filter(r -> r.active() && productId.equals(r.productId()) && r.productVariantId() == null && r.branchId() == null)
                .findFirst()
                .orElse(null);
    }

    private static BigDecimal effectiveUnitCost(InventoryItem item, InventoryBalance bal) {
        if (bal != null && bal.averageUnitCost() != null && bal.averageUnitCost().compareTo(BigDecimal.ZERO) > 0) {
            return bal.averageUnitCost();
        }
        return item != null && item.averageCost() != null ? item.averageCost() : BigDecimal.ZERO;
    }

    private void requireValidApproval(TenantId tenantId, BranchId branchId, UUID approvalId, PermissionCode requiredPerm) {
        if (approvalId == null) {
            throw new UnauthorizedOperationException(requiredPerm, "Supervisor approval is required for this inventory action");
        }
        SupervisorApproval approval = approvalRepo.findByIdScoped(approvalId, tenantId)
                .orElseThrow(() -> new UnauthorizedOperationException(requiredPerm, "Supervisor approval not found"));
        if (!"APPROVED".equals(approval.status()) || !branchId.equals(approval.branchId())
                || approval.expiresAt().isBefore(clock.now())) {
            throw new UnauthorizedOperationException(requiredPerm, "Supervisor approval is invalid or expired");
        }
    }

    private BranchId requireViewPermission(AccessPrincipal actor) {
        if (actor == null || actor.activeBranchId() == null) {
            throw new UnauthorizedOperationException(PermissionCode.INVENTORY_VIEW, "Active branch session required");
        }
        return PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), PermissionCode.INVENTORY_VIEW);
    }

    private StockMovementDto toMovementDto(StockMovement m, TenantId tenantId, BranchId branchId, boolean includeCosts) {
        InventoryItem item = inventoryRepo.findItemScoped(m.inventoryItemId(), tenantId).orElse(null);
        InventoryLocation loc = inventoryRepo.findLocationScoped(m.stockLocationId(), tenantId, branchId).orElse(null);
        UnitOfMeasure unit = m.unitId() != null ? inventoryRepo.findUnitScoped(m.unitId(), tenantId).orElse(null) : null;
        return new StockMovementDto(
                m.id(), m.branchId().value(), m.stockLocationId(), loc != null ? loc.code() : null,
                m.inventoryItemId(), item != null ? item.sku() : null, item != null ? item.name() : null,
                m.actorUserId(), m.movementType().name(), m.direction(), m.quantityDelta(),
                m.unitId(), unit != null ? unit.code() : null, m.originalQuantity(),
                m.balanceBefore(), m.balanceAfter(), m.reservedBefore(), m.reservedAfter(),
                includeCosts ? m.unitCost() : null, includeCosts ? m.totalCost() : null,
                m.referenceType(), m.referenceId(), m.reason(), m.batchNumber(), m.expiryDate(),
                m.idempotencyKey(), m.occurredAt()
        );
    }

    private StockWasteRecordDto toWasteDto(StockWasteRecord w, TenantId tenantId, boolean includeCosts) {
        InventoryItem item = inventoryRepo.findItemScoped(w.inventoryItemId(), tenantId).orElse(null);
        UnitOfMeasure unit = inventoryRepo.findUnitScoped(w.unitId(), tenantId).orElse(null);
        return new StockWasteRecordDto(
                w.id(), w.branchId().value(), w.stockLocationId(), w.inventoryItemId(),
                item != null ? item.sku() : null, item != null ? item.name() : null,
                w.unitId(), unit != null ? unit.code() : null,
                w.originalQuantity(), w.baseQuantity(),
                includeCosts ? w.unitCost() : null, includeCosts ? w.totalCost() : null,
                w.reasonCode(), w.notes(), w.attachmentRef(), w.actorUserId(),
                w.supervisorApprovalId(), w.movementId(), w.occurredAt()
        );
    }
}
