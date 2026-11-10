package com.restaurantpos.application.inventory;

import com.restaurantpos.application.audit.AuditRecordingService;
import com.restaurantpos.application.security.PermissionPolicy;
import com.restaurantpos.domain.exception.ConcurrentModificationDomainException;
import com.restaurantpos.domain.exception.DomainException;
import com.restaurantpos.domain.exception.ResourceNotFoundException;
import com.restaurantpos.domain.exception.UnauthorizedOperationException;
import com.restaurantpos.domain.inventory.CostingService;
import com.restaurantpos.domain.inventory.InventoryBalance;
import com.restaurantpos.domain.inventory.InventoryItem;
import com.restaurantpos.domain.inventory.InventoryLocation;
import com.restaurantpos.domain.inventory.InventoryPolicy;
import com.restaurantpos.domain.inventory.StockCount;
import com.restaurantpos.domain.inventory.StockCountItem;
import com.restaurantpos.domain.inventory.StockMovementType;
import com.restaurantpos.domain.inventory.UnitConversionService;
import com.restaurantpos.domain.inventory.UnitOfMeasure;
import com.restaurantpos.domain.port.BranchEventPublishPort;
import com.restaurantpos.domain.port.InventoryRepositoryPort;
import com.restaurantpos.domain.port.SupervisorApprovalRepositoryPort;
import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.domain.security.SupervisorApproval;
import com.restaurantpos.shared.dto.inventory.CreateStockCountRequest;
import com.restaurantpos.shared.dto.inventory.StockCountActionRequest;
import com.restaurantpos.shared.dto.inventory.StockCountDto;
import com.restaurantpos.shared.dto.inventory.UpdateStockCountItemsRequest;
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
 * Physical inventory count & variance reconciliation lifecycle:
 * DRAFT -> SUBMITTED -> APPROVED (or RECOUNT_REQUESTED -> SUBMITTED) -> POSTED.
 * Posting generates explicit append-only ledger movements (`STOCK_ADJUSTMENT_IN` / `STOCK_ADJUSTMENT_OUT`)
 * and never overwrites ledger balances directly.
 */
public class StockCountService {

    private static final MathContext MC = new MathContext(18, RoundingMode.HALF_UP);

    private final InventoryRepositoryPort inventoryRepo;
    private final StockLedgerService ledgerService;
    private final SupervisorApprovalRepositoryPort approvalRepo;
    private final BranchEventPublishPort eventPublisher;
    private final AuditRecordingService auditService;
    private final SystemClockPort clock;

    public StockCountService(
            InventoryRepositoryPort inventoryRepo,
            StockLedgerService ledgerService,
            SupervisorApprovalRepositoryPort approvalRepo,
            BranchEventPublishPort eventPublisher,
            AuditRecordingService auditService,
            SystemClockPort clock
    ) {
        this.inventoryRepo = inventoryRepo;
        this.ledgerService = ledgerService;
        this.approvalRepo = approvalRepo;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.clock = clock;
    }

    public List<StockCountDto> listCounts(AccessPrincipal actor) {
        BranchId branchId = requireCountViewPermission(actor);
        boolean includeCosts = actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW);
        return inventoryRepo.findStockCountsByBranch(actor.tenantId(), branchId).stream()
                .map(c -> toDto(c, actor, branchId, includeCosts))
                .toList();
    }

    public StockCountDto createCount(AccessPrincipal actor, CreateStockCountRequest req, CorrelationId corr) {
        BranchId branchId = requireCountPermission(actor);
        InventoryLocation loc = inventoryRepo.findLocationScoped(req.stockLocationId(), actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock location not found in branch"));

        List<InventoryItem> candidates = inventoryRepo.findItemsByTenant(actor.tenantId()).stream()
                .filter(InventoryItem::active)
                .filter(i -> i.branchId() == null || i.branchId().equals(branchId))
                .filter(i -> req.categoryId() == null || req.categoryId().equals(i.categoryId()))
                .filter(i -> req.inventoryItemIds() == null || req.inventoryItemIds().isEmpty()
                        || req.inventoryItemIds().contains(i.id()))
                .toList();

        if (candidates.isEmpty()) {
            throw new DomainException("EMPTY_STOCK_COUNT", "No active inventory items matched count filter");
        }

        UUID countId = UUID.randomUUID();
        String countNumber = "CNT-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        Map<UUID, InventoryBalance> balByItem = inventoryRepo.findBalancesByBranch(actor.tenantId(), branchId).stream()
                .filter(b -> b.stockLocationId().equals(loc.id()))
                .collect(Collectors.toMap(InventoryBalance::inventoryItemId, Function.identity()));

        List<StockCountItem> countItems = candidates.stream()
                .map(item -> {
                    InventoryBalance b = balByItem.get(item.id());
                    BigDecimal sysQty = b != null ? b.onHandQuantity() : BigDecimal.ZERO.setScale(UnitConversionService.QUANTITY_SCALE, RoundingMode.HALF_UP);
                    BigDecimal unitCost = (b != null && b.averageUnitCost() != null && b.averageUnitCost().compareTo(BigDecimal.ZERO) > 0)
                            ? b.averageUnitCost()
                            : (item.averageCost() != null ? item.averageCost() : BigDecimal.ZERO);
                    return new StockCountItem(
                            UUID.randomUUID(), actor.tenantId(), countId, item.id(),
                            sysQty, null, BigDecimal.ZERO, unitCost, BigDecimal.ZERO,
                            false, null, null, 0L
                    );
                })
                .toList();

        StockCount saved = inventoryRepo.saveStockCount(new StockCount(
                countId, actor.tenantId(), branchId, countNumber, loc.id(), req.categoryId(),
                "DRAFT", actor.userId(), null, null, null, null, BigDecimal.ZERO,
                req.notes(), null, null, null, 0L, countItems
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.STOCK_COUNT_CREATED,
                "StockCount", saved.id().toString(), "{\"countNumber\":\"" + countNumber + "\"}", corr);
        return toDto(saved, actor, branchId, actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW));
    }

    public StockCountDto updateCountedItems(AccessPrincipal actor, UUID countId, UpdateStockCountItemsRequest req) {
        BranchId branchId = requireCountPermission(actor);
        StockCount existing = loadLocked(countId, actor, branchId, req.expectedVersion());
        if (!"DRAFT".equals(existing.status()) && !"RECOUNT_REQUESTED".equals(existing.status())) {
            throw new DomainException("INVALID_STOCK_COUNT_TRANSITION",
                    "Cannot edit counted quantities when stock count status is " + existing.status());
        }

        Map<UUID, UpdateStockCountItemsRequest.CountLineInput> inputByItem = req.items().stream()
                .collect(Collectors.toMap(UpdateStockCountItemsRequest.CountLineInput::inventoryItemId, Function.identity()));

        List<StockCountItem> nextItems = new ArrayList<>();
        BigDecimal totalVarianceCost = BigDecimal.ZERO;
        for (StockCountItem ci : existing.items()) {
            UpdateStockCountItemsRequest.CountLineInput in = inputByItem.get(ci.inventoryItemId());
            if (in != null) {
                BigDecimal counted = in.countedQuantity().setScale(UnitConversionService.QUANTITY_SCALE, RoundingMode.HALF_UP);
                BigDecimal varQty = counted.subtract(ci.systemQuantity()).setScale(UnitConversionService.QUANTITY_SCALE, RoundingMode.HALF_UP);
                BigDecimal varCost = varQty.multiply(ci.unitCost(), MC).setScale(CostingService.COST_SCALE, RoundingMode.HALF_UP);
                BigDecimal original = ci.originalCountedQuantity() != null ? ci.originalCountedQuantity() : counted;
                nextItems.add(new StockCountItem(
                        ci.id(), ci.tenantId(), ci.stockCountId(), ci.inventoryItemId(),
                        ci.systemQuantity(), counted, varQty, ci.unitCost(), varCost,
                        in.recountRequired(), original, in.notes(), ci.version()
                ));
                totalVarianceCost = totalVarianceCost.add(varCost);
            } else {
                nextItems.add(ci);
                totalVarianceCost = totalVarianceCost.add(ci.varianceCost());
            }
        }

        StockCount saved = inventoryRepo.saveStockCount(new StockCount(
                existing.id(), existing.tenantId(), existing.branchId(), existing.countNumber(),
                existing.stockLocationId(), existing.categoryId(), existing.status(),
                existing.createdByUserId(), existing.submittedByUserId(), existing.approvedByUserId(),
                existing.postedByUserId(), existing.approvalId(),
                totalVarianceCost.setScale(CostingService.COST_SCALE, RoundingMode.HALF_UP),
                existing.notes(), existing.submittedAt(), existing.approvedAt(), existing.postedAt(),
                existing.version(), nextItems
        ));
        return toDto(saved, actor, branchId, actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW));
    }

    public StockCountDto submitCount(AccessPrincipal actor, UUID countId, StockCountActionRequest req, CorrelationId corr) {
        BranchId branchId = requireCountPermission(actor);
        StockCount existing = loadLocked(countId, actor, branchId, req.expectedVersion());
        if (!"DRAFT".equals(existing.status()) && !"RECOUNT_REQUESTED".equals(existing.status())) {
            throw new DomainException("INVALID_STOCK_COUNT_TRANSITION",
                    "Only DRAFT or RECOUNT_REQUESTED counts can be submitted");
        }
        boolean anyMissing = existing.items().stream().anyMatch(i -> i.countedQuantity() == null);
        if (anyMissing) {
            throw new DomainException("INCOMPLETE_STOCK_COUNT", "All items must have counted quantities before submitting");
        }

        Instant now = clock.now();
        StockCount saved = inventoryRepo.saveStockCount(new StockCount(
                existing.id(), existing.tenantId(), existing.branchId(), existing.countNumber(),
                existing.stockLocationId(), existing.categoryId(), "SUBMITTED",
                existing.createdByUserId(), actor.userId(), existing.approvedByUserId(),
                existing.postedByUserId(), existing.approvalId(), existing.totalVarianceCost(),
                req.notes() != null ? req.notes() : existing.notes(),
                now, existing.approvedAt(), existing.postedAt(), existing.version(), existing.items()
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.STOCK_COUNT_SUBMITTED,
                "StockCount", saved.id().toString(), null, corr);
        return toDto(saved, actor, branchId, actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW));
    }

    public StockCountDto requestRecount(AccessPrincipal actor, UUID countId, StockCountActionRequest req, CorrelationId corr) {
        BranchId branchId = requireApprovePermission(actor);
        StockCount existing = loadLocked(countId, actor, branchId, req.expectedVersion());
        if (!"SUBMITTED".equals(existing.status()) && !"APPROVED".equals(existing.status())) {
            throw new DomainException("INVALID_STOCK_COUNT_TRANSITION",
                    "Recount can only be requested on SUBMITTED or APPROVED counts");
        }
        List<StockCountItem> marked = existing.items().stream()
                .map(ci -> new StockCountItem(
                        ci.id(), ci.tenantId(), ci.stockCountId(), ci.inventoryItemId(),
                        ci.systemQuantity(), ci.countedQuantity(), ci.varianceQuantity(),
                        ci.unitCost(), ci.varianceCost(), true, ci.originalCountedQuantity(),
                        ci.notes(), ci.version()
                ))
                .toList();
        StockCount saved = inventoryRepo.saveStockCount(new StockCount(
                existing.id(), existing.tenantId(), existing.branchId(), existing.countNumber(),
                existing.stockLocationId(), existing.categoryId(), "RECOUNT_REQUESTED",
                existing.createdByUserId(), existing.submittedByUserId(), null,
                existing.postedByUserId(), existing.approvalId(), existing.totalVarianceCost(),
                req.notes() != null ? req.notes() : existing.notes(),
                existing.submittedAt(), null, existing.postedAt(), existing.version(), marked
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.STOCK_COUNT_RECOUNT,
                "StockCount", saved.id().toString(), null, corr);
        return toDto(saved, actor, branchId, actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW));
    }

    public StockCountDto approveCount(AccessPrincipal actor, UUID countId, StockCountActionRequest req, CorrelationId corr) {
        BranchId branchId = requireApprovePermission(actor);
        StockCount existing = loadLocked(countId, actor, branchId, req.expectedVersion());
        if (!"SUBMITTED".equals(existing.status())) {
            throw new DomainException("INVALID_STOCK_COUNT_TRANSITION", "Only SUBMITTED stock counts can be approved");
        }

        InventoryPolicy policy = ledgerService.getEffectivePolicy(actor.tenantId(), branchId);
        BigDecimal maxVariancePct = computeMaxVariancePct(existing.items());
        if (maxVariancePct.compareTo(policy.countVarianceApprovalPct()) > 0 && req.supervisorApprovalId() == null
                && existing.createdByUserId().equals(actor.userId())) {
            throw new UnauthorizedOperationException(PermissionCode.INVENTORY_STOCK_COUNT_APPROVE,
                    "High stock variance exceeds " + policy.countVarianceApprovalPct() + "% threshold; independent or supervisor approval required");
        }
        if (req.supervisorApprovalId() != null) {
            SupervisorApproval approval = approvalRepo.findByIdScoped(req.supervisorApprovalId(), actor.tenantId())
                    .orElseThrow(() -> new UnauthorizedOperationException(
                            PermissionCode.INVENTORY_STOCK_COUNT_APPROVE, "Supervisor approval not found"));
            if (!"APPROVED".equals(approval.status()) || !branchId.equals(approval.branchId())
                    || approval.expiresAt().isBefore(clock.now())) {
                throw new UnauthorizedOperationException(PermissionCode.INVENTORY_STOCK_COUNT_APPROVE, "Invalid or expired supervisor approval");
            }
        }

        Instant now = clock.now();
        StockCount saved = inventoryRepo.saveStockCount(new StockCount(
                existing.id(), existing.tenantId(), existing.branchId(), existing.countNumber(),
                existing.stockLocationId(), existing.categoryId(), "APPROVED",
                existing.createdByUserId(), existing.submittedByUserId(), actor.userId(),
                existing.postedByUserId(), req.supervisorApprovalId(), existing.totalVarianceCost(),
                req.notes() != null ? req.notes() : existing.notes(),
                existing.submittedAt(), now, existing.postedAt(), existing.version(), existing.items()
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.STOCK_COUNT_APPROVED,
                "StockCount", saved.id().toString(), null, corr);
        return toDto(saved, actor, branchId, actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW));
    }

    public StockCountDto postCount(AccessPrincipal actor, UUID countId, StockCountActionRequest req, CorrelationId corr) {
        BranchId branchId = requireApprovePermission(actor);
        StockCount existing = loadLocked(countId, actor, branchId, req.expectedVersion());
        if ("POSTED".equals(existing.status())) {
            throw new DomainException("ALREADY_POSTED", "Stock count " + existing.countNumber() + " has already been posted");
        }
        if (!"APPROVED".equals(existing.status())) {
            throw new DomainException("INVALID_STOCK_COUNT_TRANSITION", "Stock count must be APPROVED before posting");
        }

        InventoryPolicy policy = ledgerService.getEffectivePolicy(actor.tenantId(), branchId);
        Map<UUID, InventoryItem> items = inventoryRepo.findItemsByTenant(actor.tenantId()).stream()
                .collect(Collectors.toMap(InventoryItem::id, Function.identity()));

        for (StockCountItem ci : existing.items()) {
            BigDecimal variance = ci.varianceQuantity();
            if (variance == null || variance.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            InventoryItem item = items.get(ci.inventoryItemId());
            if (item == null) {
                continue;
            }
            StockMovementType mType = variance.compareTo(BigDecimal.ZERO) > 0
                    ? StockMovementType.STOCK_ADJUSTMENT_IN
                    : StockMovementType.STOCK_ADJUSTMENT_OUT;
            String idempKey = "cnt-" + existing.id() + "-" + item.id();
            ledgerService.applyBalanceMutation(
                    actor.tenantId(), branchId, existing.stockLocationId(), item, actor.userId(),
                    mType, variance, item.unitId(), variance.abs(),
                    variance.compareTo(BigDecimal.ZERO) > 0 ? ci.unitCost() : null,
                    "STOCK_COUNT", existing.id(),
                    "Physical count variance for " + existing.countNumber(),
                    null, null, existing.approvalId(), null, idempKey, policy.allowNegativeStock()
            );

            BigDecimal linePct = computeLineVariancePct(ci);
            if (linePct.compareTo(policy.countVarianceApprovalPct()) > 0) {
                ledgerService.ensureAlertActive(
                        actor.tenantId(), branchId, item, existing.stockLocationId(),
                        "UNUSUAL_VARIANCE", "WARNING",
                        "Count " + existing.countNumber() + " variance " + linePct + "% on " + item.name(),
                        linePct, policy.countVarianceApprovalPct(),
                        "UNUSUAL_VARIANCE:" + existing.id() + ":" + item.id()
                );
            }
        }

        Instant now = clock.now();
        StockCount saved = inventoryRepo.saveStockCount(new StockCount(
                existing.id(), existing.tenantId(), existing.branchId(), existing.countNumber(),
                existing.stockLocationId(), existing.categoryId(), "POSTED",
                existing.createdByUserId(), existing.submittedByUserId(), existing.approvedByUserId(),
                actor.userId(), existing.approvalId(), existing.totalVarianceCost(),
                existing.notes(), existing.submittedAt(), existing.approvedAt(), now,
                existing.version(), existing.items()
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.STOCK_COUNT_POSTED,
                "StockCount", saved.id().toString(),
                "{\"totalVarianceCost\":" + saved.totalVarianceCost() + "}", corr);
        eventPublisher.publish(actor.tenantId(), branchId, "INVENTORY_BALANCE_UPDATED", saved.id());
        return toDto(saved, actor, branchId, actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW));
    }

    private StockCount loadLocked(UUID countId, AccessPrincipal actor, BranchId branchId, long expectedVersion) {
        StockCount sc = inventoryRepo.findStockCountScoped(countId, actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock count not found in branch"));
        if (sc.version() != expectedVersion) {
            throw new ConcurrentModificationDomainException(
                    "Stock count " + sc.countNumber() + " modified by another user", null);
        }
        return sc;
    }

    private static BigDecimal computeMaxVariancePct(List<StockCountItem> items) {
        BigDecimal max = BigDecimal.ZERO;
        for (StockCountItem ci : items) {
            BigDecimal pct = computeLineVariancePct(ci);
            if (pct.compareTo(max) > 0) {
                max = pct;
            }
        }
        return max;
    }

    private static BigDecimal computeLineVariancePct(StockCountItem ci) {
        if (ci.varianceQuantity() == null || ci.varianceQuantity().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (ci.systemQuantity() == null || ci.systemQuantity().compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal("100.0000");
        }
        return ci.varianceQuantity().abs().multiply(new BigDecimal("100"), MC)
                .divide(ci.systemQuantity().abs(), 4, RoundingMode.HALF_UP);
    }

    private BranchId requireCountViewPermission(AccessPrincipal actor) {
        if (actor == null || actor.activeBranchId() == null) {
            throw new UnauthorizedOperationException(PermissionCode.INVENTORY_VIEW, "Active branch session required");
        }
        return PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), PermissionCode.INVENTORY_VIEW);
    }

    private BranchId requireCountPermission(AccessPrincipal actor) {
        if (actor == null || actor.activeBranchId() == null) {
            throw new UnauthorizedOperationException(PermissionCode.INVENTORY_STOCK_COUNT, "Active branch session required");
        }
        PermissionCode needed = actor.hasPermission(PermissionCode.INVENTORY_STOCK_COUNT)
                ? PermissionCode.INVENTORY_STOCK_COUNT : PermissionCode.INVENTORY_ADJUST;
        return PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), needed);
    }

    private BranchId requireApprovePermission(AccessPrincipal actor) {
        if (actor == null || actor.activeBranchId() == null) {
            throw new UnauthorizedOperationException(PermissionCode.INVENTORY_STOCK_COUNT_APPROVE, "Active branch session required");
        }
        PermissionCode needed = actor.hasPermission(PermissionCode.INVENTORY_STOCK_COUNT_APPROVE)
                ? PermissionCode.INVENTORY_STOCK_COUNT_APPROVE : PermissionCode.INVENTORY_ADJUST;
        return PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), needed);
    }

    private StockCountDto toDto(StockCount c, AccessPrincipal actor, BranchId branchId, boolean includeCosts) {
        InventoryLocation loc = inventoryRepo.findLocationScoped(c.stockLocationId(), actor.tenantId(), branchId).orElse(null);
        Map<UUID, InventoryItem> items = inventoryRepo.findItemsByTenant(actor.tenantId()).stream()
                .collect(Collectors.toMap(InventoryItem::id, Function.identity()));
        Map<UUID, UnitOfMeasure> units = inventoryRepo.findUnitsByTenant(actor.tenantId()).stream()
                .collect(Collectors.toMap(UnitOfMeasure::id, Function.identity()));

        List<StockCountDto.StockCountItemDto> lineDtos = c.items().stream()
                .map(ci -> {
                    InventoryItem item = items.get(ci.inventoryItemId());
                    UnitOfMeasure u = item != null ? units.get(item.unitId()) : null;
                    return new StockCountDto.StockCountItemDto(
                            ci.id(), ci.inventoryItemId(), item != null ? item.sku() : null,
                            item != null ? item.name() : null, u != null ? u.code() : "?",
                            ci.systemQuantity(), ci.countedQuantity(), ci.varianceQuantity(),
                            includeCosts ? ci.unitCost() : null,
                            includeCosts ? ci.varianceCost() : null,
                            ci.recountRequired(), ci.originalCountedQuantity(), ci.notes(), ci.version()
                    );
                })
                .toList();

        return new StockCountDto(
                c.id(), c.branchId().value(), c.countNumber(), c.stockLocationId(),
                loc != null ? loc.name() : null, c.categoryId(), c.status(),
                c.createdByUserId(), c.submittedByUserId(), c.approvedByUserId(), c.postedByUserId(),
                includeCosts ? c.totalVarianceCost() : null,
                c.notes(), c.submittedAt(), c.approvedAt(), c.postedAt(), c.version(), lineDtos
        );
    }
}
