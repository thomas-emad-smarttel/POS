package com.restaurantpos.application.pos;

import com.restaurantpos.application.audit.AuditRecordingService;
import com.restaurantpos.application.security.PermissionPolicy;
import com.restaurantpos.domain.exception.ConcurrentModificationDomainException;
import com.restaurantpos.domain.exception.ResourceNotFoundException;
import com.restaurantpos.domain.exception.UnauthorizedOperationException;
import com.restaurantpos.domain.port.BranchEventPublishPort;
import com.restaurantpos.domain.port.TableRepositoryPort;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.domain.table.DiningArea;
import com.restaurantpos.domain.table.DiningTable;
import com.restaurantpos.domain.table.TableStatePolicy;
import com.restaurantpos.domain.table.TableStatus;
import com.restaurantpos.shared.dto.pos.DiningAreaDto;
import com.restaurantpos.shared.dto.pos.DiningTableDto;
import com.restaurantpos.shared.dto.pos.SaveDiningAreaRequest;
import com.restaurantpos.shared.dto.pos.SaveDiningTableRequest;
import com.restaurantpos.shared.dto.pos.UpdateTableStatusRequest;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.security.AuditAction;
import com.restaurantpos.shared.security.PermissionCode;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Application service governing branch dining areas, tables, and concurrency-safe table status transitions.
 */
public class TableManagementService {

    private final TableRepositoryPort tableRepo;
    private final AuditRecordingService auditService;
    private final BranchEventPublishPort eventPublisher;

    public TableManagementService(
            TableRepositoryPort tableRepo,
            AuditRecordingService auditService,
            BranchEventPublishPort eventPublisher
    ) {
        this.tableRepo = Objects.requireNonNull(tableRepo);
        this.auditService = Objects.requireNonNull(auditService);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    public List<DiningAreaDto> listAreas(AccessPrincipal actor) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.TABLES_VIEW);
        return tableRepo.findAreasByBranch(actor.tenantId(), branchId).stream()
                .map(a -> new DiningAreaDto(a.id(), a.code(), a.name(), a.displayOrder(), a.active(), a.version()))
                .toList();
    }

    public DiningAreaDto createArea(AccessPrincipal actor, SaveDiningAreaRequest req, CorrelationId corr) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.TABLES_MANAGE);
        DiningArea saved = tableRepo.saveArea(new DiningArea(
                UUID.randomUUID(), actor.tenantId(), branchId,
                req.code().trim(), req.name().trim(), req.displayOrder(), req.active(), 0L
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(),
                AuditAction.SETTINGS_CHANGED, "DiningArea", saved.id().toString(), null, corr);
        return new DiningAreaDto(saved.id(), saved.code(), saved.name(), saved.displayOrder(), saved.active(), saved.version());
    }

    public List<DiningTableDto> listTables(AccessPrincipal actor) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.TABLES_VIEW);
        return tableRepo.findTablesByBranch(actor.tenantId(), branchId).stream()
                .map(TableManagementService::toDto)
                .toList();
    }

    public DiningTableDto createTable(AccessPrincipal actor, SaveDiningTableRequest req, CorrelationId corr) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.TABLES_MANAGE);
        DiningArea area = tableRepo.findAreaScoped(req.diningAreaId(), actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Dining area not found in active branch"));
        DiningTable saved = tableRepo.saveTable(new DiningTable(
                UUID.randomUUID(), actor.tenantId(), branchId, area.id(), area.name(),
                req.tableCode().trim(), req.displayName().trim(), req.capacity(), req.sortOrder(),
                TableStatus.AVAILABLE, null, req.active(), 0L
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(),
                AuditAction.SETTINGS_CHANGED, "DiningTable", saved.id().toString(), null, corr);
        eventPublisher.publish(actor.tenantId(), branchId, "TABLE_UPDATED", saved.id());
        return toDto(saved);
    }

    public DiningTableDto updateTableStatus(
            AccessPrincipal actor,
            UUID tableId,
            UpdateTableStatusRequest req,
            CorrelationId corr
    ) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.TABLES_MANAGE);
        DiningTable existing = tableRepo.findTableScoped(tableId, actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found in active branch"));
        if (existing.version() != req.expectedVersion()) {
            throw new ConcurrentModificationDomainException("Table was modified by another terminal", null);
        }
        TableStatus targetStatus = TableStatus.valueOf(req.status());
        TableStatePolicy.validateManualTransition(existing, targetStatus);

        DiningTable saved = tableRepo.saveTable(new DiningTable(
                existing.id(), existing.tenantId(), existing.branchId(), existing.diningAreaId(), existing.diningAreaName(),
                existing.tableCode(), existing.displayName(), existing.capacity(), existing.sortOrder(),
                targetStatus, existing.activeOrderId(), existing.active(), existing.version()
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(),
                AuditAction.TABLE_STATUS_CHANGED, "DiningTable", saved.id().toString(),
                "{\"status\":\"" + targetStatus + "\"}", corr);
        eventPublisher.publish(actor.tenantId(), branchId,
                targetStatus == TableStatus.AVAILABLE ? "TABLE_RELEASED" : "TABLE_UPDATED", saved.id());
        return toDto(saved);
    }

    private static BranchId requireBranchPermission(AccessPrincipal actor, PermissionCode permission) {
        if (actor == null || actor.activeBranchId() == null) {
            throw new UnauthorizedOperationException(permission, "Active branch session is required");
        }
        PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), permission);
        return actor.activeBranchId();
    }

    public static DiningTableDto toDto(DiningTable t) {
        return new DiningTableDto(
                t.id(), t.diningAreaId(), t.diningAreaName(), t.tableCode(), t.displayName(),
                t.capacity(), t.sortOrder(), t.status().name(), t.activeOrderId(), t.active(), t.version()
        );
    }
}
