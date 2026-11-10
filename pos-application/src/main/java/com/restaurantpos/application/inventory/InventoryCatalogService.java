package com.restaurantpos.application.inventory;

import com.restaurantpos.application.audit.AuditRecordingService;
import com.restaurantpos.application.security.PermissionPolicy;
import com.restaurantpos.domain.exception.ConcurrentModificationDomainException;
import com.restaurantpos.domain.exception.DomainException;
import com.restaurantpos.domain.exception.ResourceNotFoundException;
import com.restaurantpos.domain.inventory.InventoryAlert;
import com.restaurantpos.domain.inventory.InventoryCategory;
import com.restaurantpos.domain.inventory.InventoryItem;
import com.restaurantpos.domain.inventory.InventoryLocation;
import com.restaurantpos.domain.inventory.InventoryPolicy;
import com.restaurantpos.domain.inventory.UnitConversion;
import com.restaurantpos.domain.inventory.UnitOfMeasure;
import com.restaurantpos.domain.port.BranchEventPublishPort;
import com.restaurantpos.domain.port.InventoryRepositoryPort;
import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.shared.dto.inventory.InventoryAlertDto;
import com.restaurantpos.shared.dto.inventory.InventoryCategoryDto;
import com.restaurantpos.shared.dto.inventory.InventoryItemDto;
import com.restaurantpos.shared.dto.inventory.InventoryLocationDto;
import com.restaurantpos.shared.dto.inventory.InventoryPolicyDto;
import com.restaurantpos.shared.dto.inventory.ResolveInventoryAlertRequest;
import com.restaurantpos.shared.dto.inventory.SaveInventoryCategoryRequest;
import com.restaurantpos.shared.dto.inventory.SaveInventoryItemRequest;
import com.restaurantpos.shared.dto.inventory.SaveInventoryLocationRequest;
import com.restaurantpos.shared.dto.inventory.SaveUnitConversionRequest;
import com.restaurantpos.shared.dto.inventory.SaveUnitRequest;
import com.restaurantpos.shared.dto.inventory.UnitConversionDto;
import com.restaurantpos.shared.dto.inventory.UnitOfMeasureDto;
import com.restaurantpos.shared.dto.inventory.UpdateInventoryPolicyRequest;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.security.AuditAction;
import com.restaurantpos.shared.security.PermissionCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manages inventory catalog metadata (units, custom conversions, categories, locations, items,
 * branch inventory policies, and deduplicated operational alerts).
 */
public class InventoryCatalogService {

    private final InventoryRepositoryPort inventoryRepo;
    private final BranchEventPublishPort eventPublisher;
    private final AuditRecordingService auditService;
    private final SystemClockPort clock;

    public InventoryCatalogService(
            InventoryRepositoryPort inventoryRepo,
            BranchEventPublishPort eventPublisher,
            AuditRecordingService auditService,
            SystemClockPort clock
    ) {
        this.inventoryRepo = inventoryRepo;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.clock = clock;
    }

    public List<UnitOfMeasureDto> listUnits(AccessPrincipal actor) {
        BranchId branchId = requireViewPermission(actor);
        return inventoryRepo.findUnitsByTenant(actor.tenantId()).stream()
                .map(u -> new UnitOfMeasureDto(u.id(), u.code(), u.name(), u.unitType(),
                        u.baseUnitId(), u.conversionFactor(), u.version()))
                .toList();
    }

    public UnitOfMeasureDto createUnit(AccessPrincipal actor, SaveUnitRequest req, CorrelationId corr) {
        BranchId branchId = requireManageItemPermission(actor);
        BigDecimal factor = req.conversionFactor() != null ? req.conversionFactor() : BigDecimal.ONE;
        if (req.baseUnitId() != null) {
            inventoryRepo.findUnitScoped(req.baseUnitId(), actor.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Base unit not found"));
        }
        UnitOfMeasure saved = inventoryRepo.saveUnit(new UnitOfMeasure(
                UUID.randomUUID(), actor.tenantId(), req.code().trim().toUpperCase(),
                req.name().trim(), req.unitType(), req.baseUnitId(), factor, 0L
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.UNIT_CONVERSION_SAVED,
                "UnitOfMeasure", saved.id().toString(), "{\"code\":\"" + saved.code() + "\"}", corr);
        return new UnitOfMeasureDto(saved.id(), saved.code(), saved.name(), saved.unitType(),
                saved.baseUnitId(), saved.conversionFactor(), saved.version());
    }

    public List<UnitConversionDto> listUnitConversions(AccessPrincipal actor) {
        requireViewPermission(actor);
        Map<UUID, UnitOfMeasure> units = inventoryRepo.findUnitsByTenant(actor.tenantId()).stream()
                .collect(Collectors.toMap(UnitOfMeasure::id, Function.identity()));
        Map<UUID, InventoryItem> items = inventoryRepo.findItemsByTenant(actor.tenantId()).stream()
                .collect(Collectors.toMap(InventoryItem::id, Function.identity()));

        return inventoryRepo.findUnitConversionsByTenant(actor.tenantId()).stream()
                .map(c -> {
                    UnitOfMeasure from = units.get(c.fromUnitId());
                    UnitOfMeasure to = units.get(c.toUnitId());
                    InventoryItem item = c.inventoryItemId() != null ? items.get(c.inventoryItemId()) : null;
                    return new UnitConversionDto(
                            c.id(), c.inventoryItemId(), item != null ? item.sku() : null,
                            c.fromUnitId(), from != null ? from.code() : "?",
                            c.toUnitId(), to != null ? to.code() : "?",
                            c.conversionFactor(), c.active()
                    );
                })
                .toList();
    }

    public UnitConversionDto saveUnitConversion(AccessPrincipal actor, SaveUnitConversionRequest req, CorrelationId corr) {
        BranchId branchId = requireManageItemPermission(actor);
        if (req.fromUnitId().equals(req.toUnitId())) {
            throw new DomainException("INVALID_UNIT_CONVERSION", "Source and target units must differ");
        }
        UnitOfMeasure from = inventoryRepo.findUnitScoped(req.fromUnitId(), actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Source unit not found"));
        UnitOfMeasure to = inventoryRepo.findUnitScoped(req.toUnitId(), actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Target unit not found"));
        InventoryItem item = null;
        if (req.inventoryItemId() != null) {
            item = inventoryRepo.findItemScoped(req.inventoryItemId(), actor.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found"));
        }

        UnitConversion saved = inventoryRepo.saveUnitConversion(new UnitConversion(
                UUID.randomUUID(), actor.tenantId(), req.inventoryItemId(),
                req.fromUnitId(), req.toUnitId(), req.conversionFactor(), req.active(), 0L
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.UNIT_CONVERSION_SAVED,
                "UnitConversion", saved.id().toString(), null, corr);
        return new UnitConversionDto(saved.id(), saved.inventoryItemId(), item != null ? item.sku() : null,
                saved.fromUnitId(), from.code(), saved.toUnitId(), to.code(), saved.conversionFactor(), saved.active());
    }

    public List<InventoryCategoryDto> listCategories(AccessPrincipal actor) {
        requireViewPermission(actor);
        return inventoryRepo.findCategoriesByTenant(actor.tenantId()).stream()
                .map(c -> new InventoryCategoryDto(c.id(), c.code(), c.name(), c.description(),
                        c.displayOrder(), c.active(), c.version()))
                .toList();
    }

    public InventoryCategoryDto createCategory(AccessPrincipal actor, SaveInventoryCategoryRequest req, CorrelationId corr) {
        BranchId branchId = requireManageItemPermission(actor);
        InventoryCategory saved = inventoryRepo.saveCategory(new InventoryCategory(
                UUID.randomUUID(), actor.tenantId(), req.code().trim().toUpperCase(),
                req.name().trim(), req.description(), req.displayOrder(), req.active(), 0L
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.INVENTORY_CATEGORY_SAVED,
                "InventoryCategory", saved.id().toString(), "{\"code\":\"" + saved.code() + "\"}", corr);
        return new InventoryCategoryDto(saved.id(), saved.code(), saved.name(), saved.description(),
                saved.displayOrder(), saved.active(), saved.version());
    }

    public List<InventoryLocationDto> listLocations(AccessPrincipal actor) {
        BranchId branchId = requireViewPermission(actor);
        return inventoryRepo.findLocationsByBranch(actor.tenantId(), branchId).stream()
                .map(l -> new InventoryLocationDto(l.id(), l.branchId().value(), l.code(), l.name(),
                        l.locationType(), l.defaultLocation(), l.active(), l.version()))
                .toList();
    }

    public InventoryLocationDto createLocation(AccessPrincipal actor, SaveInventoryLocationRequest req, CorrelationId corr) {
        BranchId branchId = requireLocationPermission(actor);
        String locType = req.locationType() != null ? req.locationType() : "MAIN_STORE";
        InventoryLocation saved = inventoryRepo.saveLocation(new InventoryLocation(
                UUID.randomUUID(), actor.tenantId(), branchId, req.code().trim().toUpperCase(),
                req.name().trim(), locType, req.defaultLocation(), req.active(), 0L
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.INVENTORY_LOCATION_SAVED,
                "InventoryLocation", saved.id().toString(), "{\"code\":\"" + saved.code() + "\"}", corr);
        return new InventoryLocationDto(saved.id(), saved.branchId().value(), saved.code(), saved.name(),
                saved.locationType(), saved.defaultLocation(), saved.active(), saved.version());
    }

    public List<InventoryItemDto> listItems(AccessPrincipal actor) {
        BranchId branchId = requireViewPermission(actor);
        boolean canViewCost = actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW);
        Map<UUID, InventoryCategory> categories = inventoryRepo.findCategoriesByTenant(actor.tenantId()).stream()
                .collect(Collectors.toMap(InventoryCategory::id, Function.identity()));
        Map<UUID, UnitOfMeasure> units = inventoryRepo.findUnitsByTenant(actor.tenantId()).stream()
                .collect(Collectors.toMap(UnitOfMeasure::id, Function.identity()));

        return inventoryRepo.findItemsByTenant(actor.tenantId()).stream()
                .filter(i -> i.branchId() == null || i.branchId().equals(branchId))
                .map(i -> toItemDto(i, categories.get(i.categoryId()), units.get(i.unitId()), canViewCost))
                .toList();
    }

    public InventoryItemDto createItem(AccessPrincipal actor, SaveInventoryItemRequest req, CorrelationId corr) {
        BranchId branchId = requireManageItemPermission(actor);
        UnitOfMeasure unit = inventoryRepo.findUnitScoped(req.unitId(), actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Base unit not found"));
        InventoryCategory category = null;
        if (req.categoryId() != null) {
            category = inventoryRepo.findCategoryScoped(req.categoryId(), actor.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory category not found"));
        }
        if (req.defaultLocationId() != null) {
            inventoryRepo.findLocationScoped(req.defaultLocationId(), actor.tenantId(), branchId)
                    .orElseThrow(() -> new ResourceNotFoundException("Default stock location not found in branch"));
        }

        BigDecimal initialCost = req.initialAverageCost() != null ? req.initialAverageCost() : BigDecimal.ZERO;
        InventoryItem saved = inventoryRepo.saveItem(new InventoryItem(
                UUID.randomUUID(),
                actor.tenantId(),
                req.branchScoped() ? branchId : null,
                req.categoryId(),
                req.defaultLocationId(),
                req.sku().trim().toUpperCase(),
                req.name().trim(),
                req.description(),
                req.itemType() != null ? req.itemType() : "RAW_INGREDIENT",
                req.barcode(),
                req.unitId(),
                req.minimumStockLevel(),
                req.reorderLevel(),
                initialCost,
                req.trackingPolicy() != null ? req.trackingPolicy() : "STANDARD",
                req.expiryTracking(),
                req.batchTracking(),
                req.negativeStockPolicy() != null ? req.negativeStockPolicy() : "INHERIT",
                req.costingMethod() != null ? req.costingMethod() : "WEIGHTED_AVERAGE",
                req.active(),
                0L
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.INVENTORY_ITEM_SAVED,
                "InventoryItem", saved.id().toString(), "{\"sku\":\"" + saved.sku() + "\"}", corr);
        boolean canViewCost = actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW);
        return toItemDto(saved, category, unit, canViewCost);
    }

    public InventoryItemDto updateItem(AccessPrincipal actor, UUID itemId, SaveInventoryItemRequest req, CorrelationId corr) {
        BranchId branchId = requireManageItemPermission(actor);
        InventoryItem existing = inventoryRepo.findItemScoped(itemId, actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found"));
        if (existing.branchId() != null && !existing.branchId().equals(branchId)) {
            throw new ResourceNotFoundException("Inventory item not found in active branch");
        }
        UnitOfMeasure unit = inventoryRepo.findUnitScoped(req.unitId(), actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Base unit not found"));
        InventoryCategory category = req.categoryId() != null
                ? inventoryRepo.findCategoryScoped(req.categoryId(), actor.tenantId())
                        .orElseThrow(() -> new ResourceNotFoundException("Inventory category not found"))
                : null;

        InventoryItem saved = inventoryRepo.saveItem(new InventoryItem(
                existing.id(), existing.tenantId(), req.branchScoped() ? branchId : existing.branchId(),
                req.categoryId(), req.defaultLocationId(), req.sku().trim().toUpperCase(), req.name().trim(),
                req.description(), req.itemType() != null ? req.itemType() : existing.itemType(),
                req.barcode(), req.unitId(), req.minimumStockLevel(), req.reorderLevel(),
                existing.averageCost(),
                req.trackingPolicy() != null ? req.trackingPolicy() : existing.trackingPolicy(),
                req.expiryTracking(), req.batchTracking(),
                req.negativeStockPolicy() != null ? req.negativeStockPolicy() : existing.negativeStockPolicy(),
                req.costingMethod() != null ? req.costingMethod() : existing.costingMethod(),
                req.active(), existing.version()
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.INVENTORY_ITEM_SAVED,
                "InventoryItem", saved.id().toString(), "{\"sku\":\"" + saved.sku() + "\"}", corr);
        return toItemDto(saved, category, unit, actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW));
    }

    public InventoryPolicyDto getPolicy(AccessPrincipal actor) {
        BranchId branchId = requireViewPermission(actor);
        InventoryPolicy policy = inventoryRepo.findPolicy(actor.tenantId(), branchId)
                .orElseGet(() -> InventoryPolicy.defaults(UUID.randomUUID(), actor.tenantId(), branchId));
        return toPolicyDto(policy);
    }

    public InventoryPolicyDto updatePolicy(AccessPrincipal actor, UpdateInventoryPolicyRequest req, CorrelationId corr) {
        BranchId branchId = requireManageItemPermission(actor);
        InventoryPolicy existing = inventoryRepo.findPolicy(actor.tenantId(), branchId).orElse(null);
        InventoryPolicy saved = inventoryRepo.savePolicy(new InventoryPolicy(
                existing != null ? existing.id() : UUID.randomUUID(),
                actor.tenantId(),
                branchId,
                req.commitmentMode(),
                req.allowNegativeStock(),
                req.lowStockAlertEnabled(),
                req.expiryWarningDays(),
                req.highWasteThresholdQty(),
                req.wasteApprovalThresholdAmount(),
                req.countVarianceApprovalPct(),
                req.transferApprovalRequired(),
                req.allowManualConsumptionReverse(),
                existing != null ? existing.version() : 0L
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.INVENTORY_POLICY_UPDATED,
                "InventoryPolicy", saved.id().toString(),
                "{\"commitmentMode\":\"" + saved.commitmentMode() + "\"}", corr);
        return toPolicyDto(saved);
    }

    public List<InventoryAlertDto> listAlerts(AccessPrincipal actor, boolean activeOnly) {
        BranchId branchId = requireViewPermission(actor);
        Map<UUID, InventoryItem> items = inventoryRepo.findItemsByTenant(actor.tenantId()).stream()
                .collect(Collectors.toMap(InventoryItem::id, Function.identity()));
        return inventoryRepo.findAlertsByBranch(actor.tenantId(), branchId, activeOnly).stream()
                .map(a -> toAlertDto(a, items.get(a.inventoryItemId())))
                .toList();
    }

    public InventoryAlertDto resolveAlert(AccessPrincipal actor, UUID alertId, ResolveInventoryAlertRequest req, CorrelationId corr) {
        BranchId branchId = PermissionPolicy.requireBranchPermission(
                actor, actor.tenantId(), actor.activeBranchId(),
                actor.hasPermission(PermissionCode.INVENTORY_ALERT_MANAGE) ? PermissionCode.INVENTORY_ALERT_MANAGE : PermissionCode.INVENTORY_MANAGE
        );
        InventoryAlert existing = inventoryRepo.findAlertScoped(alertId, actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory alert not found in branch"));
        if (existing.version() != req.expectedVersion()) {
            throw new ConcurrentModificationDomainException("Inventory alert version conflict", null);
        }
        String nextStatus = req.targetStatus() != null ? req.targetStatus() : "RESOLVED";
        Instant now = clock.now();
        InventoryAlert saved = inventoryRepo.saveAlert(new InventoryAlert(
                existing.id(), existing.tenantId(), existing.branchId(), existing.inventoryItemId(),
                existing.stockLocationId(), existing.alertType(), existing.severity(), existing.message(),
                existing.currentValue(), existing.thresholdValue(), nextStatus, existing.dedupKey(),
                "RESOLVED".equals(nextStatus) ? actor.userId() : null,
                "RESOLVED".equals(nextStatus) ? now : null,
                existing.createdAt(), existing.version()
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.INVENTORY_ALERT_RESOLVED,
                "InventoryAlert", saved.id().toString(), "{\"status\":\"" + nextStatus + "\"}", corr);
        eventPublisher.publish(actor.tenantId(), branchId, "INVENTORY_ALERT_UPDATED", saved.id());
        InventoryItem item = inventoryRepo.findItemScoped(saved.inventoryItemId(), actor.tenantId()).orElse(null);
        return toAlertDto(saved, item);
    }

    private BranchId requireViewPermission(AccessPrincipal actor) {
        if (actor == null || actor.activeBranchId() == null) {
            throw new com.restaurantpos.domain.exception.UnauthorizedOperationException(
                    PermissionCode.INVENTORY_VIEW, "Active branch session is required");
        }
        return PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), PermissionCode.INVENTORY_VIEW);
    }

    private BranchId requireManageItemPermission(AccessPrincipal actor) {
        if (actor == null || actor.activeBranchId() == null) {
            throw new com.restaurantpos.domain.exception.UnauthorizedOperationException(
                    PermissionCode.INVENTORY_ITEM_MANAGE, "Active branch session is required");
        }
        PermissionCode needed = actor.hasPermission(PermissionCode.INVENTORY_ITEM_MANAGE)
                ? PermissionCode.INVENTORY_ITEM_MANAGE : PermissionCode.INVENTORY_MANAGE;
        return PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), needed);
    }

    private BranchId requireLocationPermission(AccessPrincipal actor) {
        if (actor == null || actor.activeBranchId() == null) {
            throw new com.restaurantpos.domain.exception.UnauthorizedOperationException(
                    PermissionCode.INVENTORY_LOCATION_MANAGE, "Active branch session is required");
        }
        PermissionCode needed = actor.hasPermission(PermissionCode.INVENTORY_LOCATION_MANAGE)
                ? PermissionCode.INVENTORY_LOCATION_MANAGE : PermissionCode.INVENTORY_MANAGE;
        return PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), needed);
    }

    private static InventoryItemDto toItemDto(InventoryItem i, InventoryCategory c, UnitOfMeasure u, boolean includeCost) {
        return new InventoryItemDto(
                i.id(), i.branchId() != null ? i.branchId().value() : null,
                i.categoryId(), c != null ? c.name() : null, i.defaultLocationId(),
                i.sku(), i.name(), i.description(), i.itemType(), i.barcode(),
                i.unitId(), u != null ? u.code() : "?",
                i.minimumStockLevel(), i.reorderLevel(),
                includeCost ? i.averageCost() : null,
                i.trackingPolicy(), i.expiryTracking(), i.batchTracking(),
                i.negativeStockPolicy(), i.costingMethod(), i.active(), i.version()
        );
    }

    private static InventoryPolicyDto toPolicyDto(InventoryPolicy p) {
        return new InventoryPolicyDto(
                p.id(), p.branchId().value(), p.commitmentMode(), p.allowNegativeStock(),
                p.lowStockAlertEnabled(), p.expiryWarningDays(), p.highWasteThresholdQty(),
                p.wasteApprovalThresholdAmount(), p.countVarianceApprovalPct(),
                p.transferApprovalRequired(), p.allowManualConsumptionReverse(), p.version()
        );
    }

    private static InventoryAlertDto toAlertDto(InventoryAlert a, InventoryItem item) {
        return new InventoryAlertDto(
                a.id(), a.branchId().value(), a.inventoryItemId(),
                item != null ? item.sku() : null, item != null ? item.name() : null,
                a.stockLocationId(), a.alertType(), a.severity(), a.message(),
                a.currentValue(), a.thresholdValue(), a.status(),
                a.createdAt(), a.resolvedAt(), a.version()
        );
    }
}
