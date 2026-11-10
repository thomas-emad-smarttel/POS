package com.restaurantpos.application.inventory;

import com.restaurantpos.application.audit.AuditRecordingService;
import com.restaurantpos.domain.exception.ConcurrentModificationDomainException;
import com.restaurantpos.domain.exception.DomainException;
import com.restaurantpos.domain.exception.ResourceNotFoundException;
import com.restaurantpos.domain.exception.UnauthorizedOperationException;
import com.restaurantpos.domain.inventory.InventoryBalance;
import com.restaurantpos.domain.inventory.InventoryItem;
import com.restaurantpos.domain.inventory.InventoryLocation;
import com.restaurantpos.domain.inventory.InventoryPolicy;
import com.restaurantpos.domain.inventory.StockMovement;
import com.restaurantpos.domain.inventory.StockMovementType;
import com.restaurantpos.domain.inventory.StockTransfer;
import com.restaurantpos.domain.inventory.StockTransferItem;
import com.restaurantpos.domain.inventory.UnitConversion;
import com.restaurantpos.domain.inventory.UnitConversionService;
import com.restaurantpos.domain.inventory.UnitOfMeasure;
import com.restaurantpos.domain.port.BranchEventPublishPort;
import com.restaurantpos.domain.port.InventoryRepositoryPort;
import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.shared.dto.inventory.CreateStockTransferRequest;
import com.restaurantpos.shared.dto.inventory.ReceiveStockTransferRequest;
import com.restaurantpos.shared.dto.inventory.StockTransferActionRequest;
import com.restaurantpos.shared.dto.inventory.StockTransferDto;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.security.AuditAction;
import com.restaurantpos.shared.security.PermissionCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Inter-branch and intra-branch stock transfers:
 * REQUESTED -> APPROVED -> DISPATCHED (TRANSFER_OUT at source) -> PARTIALLY_RECEIVED / RECEIVED (TRANSFER_IN at destination).
 * Enforces strict tenant isolation, branch access checks on source/destination, over-receipt blocking, and damaged/missing tracking.
 */
public class StockTransferService {

    private final InventoryRepositoryPort inventoryRepo;
    private final StockLedgerService ledgerService;
    private final BranchEventPublishPort eventPublisher;
    private final AuditRecordingService auditService;
    private final SystemClockPort clock;

    public StockTransferService(
            InventoryRepositoryPort inventoryRepo,
            StockLedgerService ledgerService,
            BranchEventPublishPort eventPublisher,
            AuditRecordingService auditService,
            SystemClockPort clock
    ) {
        this.inventoryRepo = inventoryRepo;
        this.ledgerService = ledgerService;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.clock = clock;
    }

    public List<StockTransferDto> listTransfers(AccessPrincipal actor) {
        BranchId branchId = InventoryAuthHelper.requireActiveBranchPermission(actor, PermissionCode.INVENTORY_VIEW, null);
        boolean includeCosts = actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW);
        return inventoryRepo.findStockTransfersByTenant(actor.tenantId()).stream()
                .filter(t -> t.sourceBranchId().equals(branchId) || t.destinationBranchId().equals(branchId))
                .map(t -> toDto(t, actor, includeCosts))
                .toList();
    }

    public StockTransferDto createTransfer(AccessPrincipal actor, CreateStockTransferRequest req, CorrelationId corr) {
        BranchId sourceBranch = InventoryAuthHelper.requireActiveBranchPermission(
                actor, PermissionCode.INVENTORY_STOCK_TRANSFER, PermissionCode.INVENTORY_TRANSFER);

        InventoryLocation srcLoc = inventoryRepo.findLocationScoped(req.sourceLocationId(), actor.tenantId(), sourceBranch)
                .orElseThrow(() -> new ResourceNotFoundException("Source location not found in active branch"));
        InventoryLocation dstLoc = inventoryRepo.findLocationInTenant(req.destinationLocationId(), actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination location not found in tenant"));
        if (!dstLoc.branchId().value().equals(req.destinationBranchId())) {
            throw new DomainException("INVALID_TRANSFER", "Destination location does not belong to specified destination branch");
        }
        if (srcLoc.id().equals(dstLoc.id())) {
            throw new DomainException("INVALID_TRANSFER", "Source and destination locations must differ");
        }

        UUID transferId = UUID.randomUUID();
        String transferNumber = "TRF-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        List<UnitOfMeasure> units = inventoryRepo.findUnitsByTenant(actor.tenantId());
        List<UnitConversion> conversions = inventoryRepo.findUnitConversionsByTenant(actor.tenantId());

        List<StockTransferItem> transferItems = new ArrayList<>();
        for (CreateStockTransferRequest.TransferLineInput in : req.items()) {
            InventoryItem item = inventoryRepo.findItemScoped(in.inventoryItemId(), actor.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found"));
            // Validate unit convertibility immediately
            UnitConversionService.convert(in.quantity(), in.unitId(), item.unitId(), item.id(), units, conversions);
            InventoryBalance bal = inventoryRepo.findBalanceScoped(actor.tenantId(), sourceBranch, srcLoc.id(), item.id()).orElse(null);
            BigDecimal unitCost = (bal != null && bal.averageUnitCost() != null && bal.averageUnitCost().compareTo(BigDecimal.ZERO) > 0)
                    ? bal.averageUnitCost()
                    : (item.averageCost() != null ? item.averageCost() : BigDecimal.ZERO);

            transferItems.add(new StockTransferItem(
                    UUID.randomUUID(), actor.tenantId(), transferId, item.id(), in.unitId(),
                    in.quantity().setScale(UnitConversionService.QUANTITY_SCALE, RoundingMode.HALF_UP),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    unitCost, in.notes(), 0L
            ));
        }

        Instant now = clock.now();
        StockTransfer saved = inventoryRepo.saveStockTransfer(new StockTransfer(
                transferId, actor.tenantId(), transferNumber, sourceBranch, srcLoc.id(),
                dstLoc.branchId(), dstLoc.id(), "REQUESTED", actor.userId(),
                null, null, null, req.notes(), now, null, null, null, 0L, transferItems
        ));
        auditService.record(actor.tenantId(), sourceBranch, actor.userId(), AuditAction.STOCK_TRANSFER_REQUESTED,
                "StockTransfer", saved.id().toString(), "{\"transferNumber\":\"" + transferNumber + "\"}", corr);
        return toDto(saved, actor, actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW));
    }

    public StockTransferDto approveTransfer(AccessPrincipal actor, UUID transferId, StockTransferActionRequest req, CorrelationId corr) {
        BranchId branchId = InventoryAuthHelper.requireActiveBranchPermission(
                actor, PermissionCode.INVENTORY_STOCK_TRANSFER_APPROVE, PermissionCode.INVENTORY_TRANSFER);

        StockTransfer existing = loadLocked(transferId, actor, req.expectedVersion());
        if (!existing.sourceBranchId().equals(branchId) && !existing.destinationBranchId().equals(branchId)) {
            throw new ResourceNotFoundException("Stock transfer not found for active branch");
        }
        if (!"REQUESTED".equals(existing.status())) {
            throw new DomainException("INVALID_TRANSFER_TRANSITION", "Only REQUESTED transfers can be approved");
        }
        Instant now = clock.now();
        StockTransfer saved = inventoryRepo.saveStockTransfer(new StockTransfer(
                existing.id(), existing.tenantId(), existing.transferNumber(),
                existing.sourceBranchId(), existing.sourceLocationId(),
                existing.destinationBranchId(), existing.destinationLocationId(),
                "APPROVED", existing.requestedByUserId(), actor.userId(),
                existing.dispatchedByUserId(), existing.receivedByUserId(),
                req.notes() != null ? req.notes() : existing.notes(),
                existing.requestedAt(), now, existing.dispatchedAt(), existing.receivedAt(),
                existing.version(), existing.items()
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.STOCK_TRANSFER_APPROVED,
                "StockTransfer", saved.id().toString(), null, corr);
        return toDto(saved, actor, actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW));
    }

    public StockTransferDto dispatchTransfer(AccessPrincipal actor, UUID transferId, StockTransferActionRequest req, CorrelationId corr) {
        PermissionCode needed = actor.hasPermission(PermissionCode.INVENTORY_STOCK_TRANSFER)
                ? PermissionCode.INVENTORY_STOCK_TRANSFER : PermissionCode.INVENTORY_TRANSFER;
        BranchId sourceBranch = InventoryAuthHelper.requireActiveBranchPermission(
                actor, PermissionCode.INVENTORY_STOCK_TRANSFER, PermissionCode.INVENTORY_TRANSFER);

        StockTransfer existing = loadLocked(transferId, actor, req.expectedVersion());
        if (!existing.sourceBranchId().equals(sourceBranch)) {
            throw new UnauthorizedOperationException(needed, "Transfer can only be dispatched from source branch");
        }
        InventoryPolicy policy = ledgerService.getEffectivePolicy(actor.tenantId(), sourceBranch);
        if ("REQUESTED".equals(existing.status()) && policy.transferApprovalRequired()) {
            throw new DomainException("TRANSFER_APPROVAL_REQUIRED", "Transfer must be approved before dispatching");
        }
        if (!"APPROVED".equals(existing.status()) && !"REQUESTED".equals(existing.status())) {
            throw new DomainException("INVALID_TRANSFER_TRANSITION", "Transfer cannot be dispatched from status " + existing.status());
        }

        List<UnitOfMeasure> units = inventoryRepo.findUnitsByTenant(actor.tenantId());
        List<UnitConversion> conversions = inventoryRepo.findUnitConversionsByTenant(actor.tenantId());
        List<StockTransferItem> dispatchedItems = new ArrayList<>();

        for (StockTransferItem ti : existing.items()) {
            InventoryItem item = inventoryRepo.findItemScoped(ti.inventoryItemId(), actor.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found"));
            BigDecimal baseQty = UnitConversionService.convert(
                            ti.requestedQuantity(), ti.unitId(), item.unitId(), item.id(), units, conversions)
                    .setScale(UnitConversionService.QUANTITY_SCALE, RoundingMode.HALF_UP);

            StockMovement outMv = ledgerService.postTransferOutMovement(
                    actor.tenantId(), existing.sourceBranchId(), existing.sourceLocationId(),
                    item, actor.userId(), baseQty, ti.unitId(), ti.requestedQuantity(),
                    existing.id(), "Transfer Out " + existing.transferNumber(),
                    "trf-out-" + existing.id() + "-" + item.id()
            );
            dispatchedItems.add(new StockTransferItem(
                    ti.id(), ti.tenantId(), ti.stockTransferId(), ti.inventoryItemId(), ti.unitId(),
                    ti.requestedQuantity(), ti.requestedQuantity(), ti.receivedQuantity(),
                    ti.damagedQuantity(), ti.missingQuantity(), outMv.unitCost(), ti.notes(), ti.version()
            ));
        }

        Instant now = clock.now();
        StockTransfer saved = inventoryRepo.saveStockTransfer(new StockTransfer(
                existing.id(), existing.tenantId(), existing.transferNumber(),
                existing.sourceBranchId(), existing.sourceLocationId(),
                existing.destinationBranchId(), existing.destinationLocationId(),
                "DISPATCHED", existing.requestedByUserId(), existing.approvedByUserId(),
                actor.userId(), existing.receivedByUserId(),
                req.notes() != null ? req.notes() : existing.notes(),
                existing.requestedAt(), existing.approvedAt(), now, existing.receivedAt(),
                existing.version(), dispatchedItems
        ));
        auditService.record(actor.tenantId(), sourceBranch, actor.userId(), AuditAction.STOCK_TRANSFER_DISPATCHED,
                "StockTransfer", saved.id().toString(), null, corr);
        eventPublisher.publish(actor.tenantId(), existing.sourceBranchId(), "INVENTORY_TRANSFER_DISPATCHED", saved.id());
        return toDto(saved, actor, actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW));
    }

    public StockTransferDto receiveTransfer(AccessPrincipal actor, UUID transferId, ReceiveStockTransferRequest req, CorrelationId corr) {
        PermissionCode needed = actor.hasPermission(PermissionCode.INVENTORY_STOCK_RECEIVE)
                ? PermissionCode.INVENTORY_STOCK_RECEIVE : PermissionCode.INVENTORY_TRANSFER;
        BranchId destBranch = InventoryAuthHelper.requireActiveBranchPermission(
                actor, PermissionCode.INVENTORY_STOCK_RECEIVE, PermissionCode.INVENTORY_TRANSFER);

        StockTransfer existing = loadLocked(transferId, actor, req.expectedVersion());
        if (!existing.destinationBranchId().equals(destBranch)) {
            throw new UnauthorizedOperationException(needed, "Transfer can only be received at destination branch");
        }
        if (!"DISPATCHED".equals(existing.status()) && !"PARTIALLY_RECEIVED".equals(existing.status())) {
            throw new DomainException("INVALID_TRANSFER_TRANSITION", "Transfer must be DISPATCHED before receiving");
        }

        Map<UUID, ReceiveStockTransferRequest.ReceiveLineInput> inputByLine = req.lines().stream()
                .collect(Collectors.toMap(ReceiveStockTransferRequest.ReceiveLineInput::transferItemId, Function.identity()));
        List<UnitOfMeasure> units = inventoryRepo.findUnitsByTenant(actor.tenantId());
        List<UnitConversion> conversions = inventoryRepo.findUnitConversionsByTenant(actor.tenantId());

        List<StockTransferItem> updatedItems = new ArrayList<>();
        boolean allAccounted = true;
        Instant now = clock.now();

        for (StockTransferItem ti : existing.items()) {
            ReceiveStockTransferRequest.ReceiveLineInput in = inputByLine.get(ti.id());
            if (in == null) {
                updatedItems.add(ti);
                BigDecimal accounted = ti.receivedQuantity().add(ti.damagedQuantity()).add(ti.missingQuantity());
                if (accounted.compareTo(ti.dispatchedQuantity()) < 0) {
                    allAccounted = false;
                }
                continue;
            }
            BigDecimal addRecv = in.receivedQuantity().setScale(UnitConversionService.QUANTITY_SCALE, RoundingMode.HALF_UP);
            BigDecimal addDmg = in.damagedQuantity().setScale(UnitConversionService.QUANTITY_SCALE, RoundingMode.HALF_UP);
            BigDecimal addMiss = in.missingQuantity().setScale(UnitConversionService.QUANTITY_SCALE, RoundingMode.HALF_UP);

            BigDecimal newRecv = ti.receivedQuantity().add(addRecv);
            BigDecimal newDmg = ti.damagedQuantity().add(addDmg);
            BigDecimal newMiss = ti.missingQuantity().add(addMiss);
            BigDecimal newAccounted = newRecv.add(newDmg).add(newMiss);

            if (newAccounted.compareTo(ti.dispatchedQuantity()) > 0) {
                throw new DomainException(
                        "INVALID_TRANSFER_QUANTITY",
                        "Accounted quantity (" + newAccounted + ") cannot exceed dispatched quantity (" + ti.dispatchedQuantity() + ")"
                );
            }
            if (newAccounted.compareTo(ti.dispatchedQuantity()) < 0) {
                allAccounted = false;
            }

            InventoryItem item = inventoryRepo.findItemScoped(ti.inventoryItemId(), actor.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found"));

            if (addRecv.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal baseRecv = UnitConversionService.convert(
                                addRecv, ti.unitId(), item.unitId(), item.id(), units, conversions)
                        .setScale(UnitConversionService.QUANTITY_SCALE, RoundingMode.HALF_UP);
                String idempKey = "trf-in-" + existing.id() + "-" + ti.id() + "-" + now.toEpochMilli();
                ledgerService.postTransferInMovement(
                        actor.tenantId(), existing.destinationBranchId(), existing.destinationLocationId(),
                        item, actor.userId(), baseRecv, ti.unitId(), addRecv,
                        ti.unitCost(), existing.id(), "Transfer In " + existing.transferNumber(), idempKey
                );
            }
            if (addDmg.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal baseDmg = UnitConversionService.convert(
                                addDmg, ti.unitId(), item.unitId(), item.id(), units, conversions)
                        .setScale(UnitConversionService.QUANTITY_SCALE, RoundingMode.HALF_UP);
                ledgerService.applyBalanceMutation(
                        actor.tenantId(), existing.destinationBranchId(), existing.destinationLocationId(),
                        item, actor.userId(), StockMovementType.DAMAGE, baseDmg.negate(), ti.unitId(), addDmg,
                        ti.unitCost(), "STOCK_TRANSFER_DAMAGE", existing.id(),
                        "Damaged in transit on " + existing.transferNumber(),
                        null, null, null, null, "trf-dmg-" + existing.id() + "-" + ti.id() + "-" + now.toEpochMilli(), true
                );
            }

            updatedItems.add(new StockTransferItem(
                    ti.id(), ti.tenantId(), ti.stockTransferId(), ti.inventoryItemId(), ti.unitId(),
                    ti.requestedQuantity(), ti.dispatchedQuantity(), newRecv, newDmg, newMiss,
                    ti.unitCost(), in.notes() != null ? in.notes() : ti.notes(), ti.version()
            ));
        }

        String nextStatus = allAccounted ? "RECEIVED" : "PARTIALLY_RECEIVED";
        StockTransfer saved = inventoryRepo.saveStockTransfer(new StockTransfer(
                existing.id(), existing.tenantId(), existing.transferNumber(),
                existing.sourceBranchId(), existing.sourceLocationId(),
                existing.destinationBranchId(), existing.destinationLocationId(),
                nextStatus, existing.requestedByUserId(), existing.approvedByUserId(),
                existing.dispatchedByUserId(), actor.userId(),
                req.notes() != null ? req.notes() : existing.notes(),
                existing.requestedAt(), existing.approvedAt(), existing.dispatchedAt(), now,
                existing.version(), updatedItems
        ));
        auditService.record(actor.tenantId(), destBranch, actor.userId(), AuditAction.STOCK_TRANSFER_RECEIVED,
                "StockTransfer", saved.id().toString(), "{\"status\":\"" + nextStatus + "\"}", corr);
        eventPublisher.publish(actor.tenantId(), destBranch, "INVENTORY_BALANCE_UPDATED", saved.id());
        return toDto(saved, actor, actor.hasPermission(PermissionCode.INVENTORY_COST_VIEW));
    }

    private StockTransfer loadLocked(UUID transferId, AccessPrincipal actor, long expectedVersion) {
        StockTransfer trf = inventoryRepo.findStockTransferScoped(transferId, actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Stock transfer not found"));
        if (trf.version() != expectedVersion) {
            throw new ConcurrentModificationDomainException(
                    "Stock transfer " + trf.transferNumber() + " was modified concurrently", null);
        }
        return trf;
    }

    private StockTransferDto toDto(StockTransfer t, AccessPrincipal actor, boolean includeCosts) {
        InventoryLocation src = inventoryRepo.findLocationInTenant(t.sourceLocationId(), actor.tenantId()).orElse(null);
        InventoryLocation dst = inventoryRepo.findLocationInTenant(t.destinationLocationId(), actor.tenantId()).orElse(null);
        Map<UUID, InventoryItem> items = inventoryRepo.findItemsByTenant(actor.tenantId()).stream()
                .collect(Collectors.toMap(InventoryItem::id, Function.identity()));
        Map<UUID, UnitOfMeasure> units = inventoryRepo.findUnitsByTenant(actor.tenantId()).stream()
                .collect(Collectors.toMap(UnitOfMeasure::id, Function.identity()));

        List<StockTransferDto.StockTransferItemDto> itemDtos = t.items().stream()
                .map(ti -> {
                    InventoryItem item = items.get(ti.inventoryItemId());
                    UnitOfMeasure u = units.get(ti.unitId());
                    return new StockTransferDto.StockTransferItemDto(
                            ti.id(), ti.inventoryItemId(), item != null ? item.sku() : null,
                            item != null ? item.name() : null, ti.unitId(), u != null ? u.code() : "?",
                            ti.requestedQuantity(), ti.dispatchedQuantity(), ti.receivedQuantity(),
                            ti.damagedQuantity(), ti.missingQuantity(),
                            includeCosts ? ti.unitCost() : null, ti.notes(), ti.version()
                    );
                })
                .toList();

        return new StockTransferDto(
                t.id(), t.transferNumber(), t.sourceBranchId().value(), t.sourceLocationId(),
                src != null ? src.name() : null, t.destinationBranchId().value(), t.destinationLocationId(),
                dst != null ? dst.name() : null, t.status(),
                t.requestedByUserId(), t.approvedByUserId(), t.dispatchedByUserId(), t.receivedByUserId(),
                t.notes(), t.requestedAt(), t.approvedAt(), t.dispatchedAt(), t.receivedAt(),
                t.version(), itemDtos
        );
    }
}
