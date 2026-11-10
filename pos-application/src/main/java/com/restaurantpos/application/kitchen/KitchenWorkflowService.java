package com.restaurantpos.application.kitchen;

import com.restaurantpos.application.audit.AuditRecordingService;
import com.restaurantpos.application.security.PermissionPolicy;
import com.restaurantpos.domain.exception.ConcurrentModificationDomainException;
import com.restaurantpos.domain.exception.DomainException;
import com.restaurantpos.domain.exception.ResourceNotFoundException;
import com.restaurantpos.domain.exception.UnauthorizedOperationException;
import com.restaurantpos.domain.kitchen.KitchenPrintJob;
import com.restaurantpos.domain.kitchen.KitchenPriorityLevel;
import com.restaurantpos.domain.kitchen.KitchenStation;
import com.restaurantpos.domain.kitchen.KitchenTicket;
import com.restaurantpos.domain.kitchen.KitchenTicketEvent;
import com.restaurantpos.domain.kitchen.KitchenTicketItem;
import com.restaurantpos.domain.kitchen.KitchenTicketItemStatus;
import com.restaurantpos.domain.kitchen.KitchenTicketStatePolicy;
import com.restaurantpos.domain.kitchen.KitchenTicketStatus;
import com.restaurantpos.domain.port.BranchEventPublishPort;
import com.restaurantpos.domain.port.InventoryDispatchPort;
import com.restaurantpos.domain.port.InventoryPayloadCodecPort;
import com.restaurantpos.domain.port.KitchenRepositoryPort;
import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.shared.dto.kitchen.ExpoOrderDto;
import com.restaurantpos.shared.dto.kitchen.KitchenPrintJobDto;
import com.restaurantpos.shared.dto.kitchen.KitchenTicketActionRequest;
import com.restaurantpos.shared.dto.kitchen.KitchenTicketDto;
import com.restaurantpos.shared.dto.kitchen.KitchenTicketEventDto;
import com.restaurantpos.shared.dto.kitchen.KitchenTicketItemDto;
import com.restaurantpos.shared.dto.kitchen.UpdateKitchenItemStatusRequest;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.security.AuditAction;
import com.restaurantpos.shared.security.PermissionCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Kitchen Display System & Expo ticket lifecycle orchestrator with optimistic locking,
 * item-level partial readiness, auditable re-fire/hold/cancel transitions, and Expo readiness checks.
 */
public class KitchenWorkflowService {

    private final KitchenRepositoryPort kitchenRepo;
    private final KitchenPrintDispatcher printDispatcher;
    private final BranchEventPublishPort eventPublisher;
    private final AuditRecordingService auditService;
    private final SystemClockPort clock;
    private final InventoryDispatchPort inventoryDispatch;

    public KitchenWorkflowService(
            KitchenRepositoryPort kitchenRepo,
            KitchenPrintDispatcher printDispatcher,
            BranchEventPublishPort eventPublisher,
            AuditRecordingService auditService,
            SystemClockPort clock
    ) {
        this(kitchenRepo, printDispatcher, eventPublisher, auditService, clock, new InventoryDispatchPort() {});
    }

    public KitchenWorkflowService(
            KitchenRepositoryPort kitchenRepo,
            KitchenPrintDispatcher printDispatcher,
            BranchEventPublishPort eventPublisher,
            AuditRecordingService auditService,
            SystemClockPort clock,
            InventoryDispatchPort inventoryDispatch
    ) {
        this.kitchenRepo = kitchenRepo;
        this.printDispatcher = printDispatcher;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.clock = clock;
        this.inventoryDispatch = inventoryDispatch != null ? inventoryDispatch : new InventoryDispatchPort() {};
    }

    public List<KitchenTicketDto> listStationTickets(AccessPrincipal actor, UUID stationId, String sortMode) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.KITCHEN_VIEW);
        KitchenStation station = kitchenRepo.findStationScoped(stationId, actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Kitchen station not found in branch"));
        Instant now = clock.now();
        List<KitchenTicketDto> dtos = kitchenRepo.findActiveTicketsByStation(actor.tenantId(), branchId, station.id()).stream()
                .map(t -> toTicketDto(t, station, now))
                .toList();
        return sortTickets(dtos, sortMode);
    }

    public KitchenTicketDto getTicket(AccessPrincipal actor, UUID ticketId) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.KITCHEN_VIEW);
        KitchenTicket ticket = kitchenRepo.findTicketScoped(ticketId, actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Kitchen ticket not found in branch"));
        KitchenStation station = kitchenRepo.findStationScoped(ticket.kitchenStationId(), actor.tenantId(), branchId).orElse(null);
        return toTicketDto(ticket, station, clock.now());
    }

    public List<KitchenTicketEventDto> getTicketEvents(AccessPrincipal actor, UUID ticketId) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.KITCHEN_VIEW);
        kitchenRepo.findTicketScoped(ticketId, actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Kitchen ticket not found in branch"));
        return kitchenRepo.findTicketEvents(ticketId, actor.tenantId(), branchId).stream()
                .map(e -> new KitchenTicketEventDto(
                        e.id(), e.kitchenTicketId(), e.kitchenTicketItemId(), e.actorUserId(),
                        e.eventType(), e.previousStatus(), e.newStatus(), e.reason(), e.detailsJson(), e.occurredAt()
                ))
                .toList();
    }

    public KitchenTicketDto acceptTicket(AccessPrincipal actor, UUID ticketId, KitchenTicketActionRequest req, CorrelationId corr) {
        BranchId branchId = requireAnyKitchenPermission(actor, PermissionCode.KITCHEN_TICKET_ACCEPT, PermissionCode.KITCHEN_UPDATE);
        KitchenTicket ticket = loadLockedTicket(ticketId, actor, branchId, req.expectedVersion());
        KitchenTicketStatePolicy.requireTicketTransition(ticket.status(), KitchenTicketStatus.ACCEPTED);

        Instant now = clock.now();
        List<KitchenTicketItem> queuedItems = ticket.items().stream()
                .map(i -> i.status() == KitchenTicketItemStatus.NEW
                        ? withItemStatus(i, KitchenTicketItemStatus.QUEUED, i.startedAt(), i.readyAt(), i.servedAt(), null)
                        : i)
                .toList();

        KitchenTicket saved = kitchenRepo.saveTicket(new KitchenTicket(
                ticket.id(), ticket.tenantId(), ticket.branchId(), ticket.orderId(),
                ticket.orderNumberSnapshot(), ticket.orderTypeSnapshot(), ticket.tableLabelSnapshot(),
                ticket.waiterId(), ticket.waiterNameSnapshot(), ticket.kitchenStationId(),
                ticket.sourceOutboxEventId(), ticket.ticketNumber(), ticket.ticketKind(),
                KitchenTicketStatus.ACCEPTED, ticket.priorityLevel(), ticket.priorityScore(),
                ticket.expectedPrepMinutes(), ticket.firedAt(), now,
                ticket.startedAt(), ticket.readyAt(), ticket.bumpedAt(), ticket.cancelledAt(),
                ticket.cancelledByUserId(), ticket.holdReason(), ticket.cancelReason(),
                ticket.version(), queuedItems
        ));

        kitchenRepo.appendTicketEvent(new KitchenTicketEvent(
                UUID.randomUUID(), actor.tenantId(), branchId, saved.id(), null, actor.userId(),
                "ACCEPTED", ticket.status().name(), "ACCEPTED", req.reason(), null, now
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.KITCHEN_TICKET_ACCEPTED,
                "KitchenTicket", saved.id().toString(), null, corr);
        eventPublisher.publish(actor.tenantId(), branchId, "KITCHEN_TICKET_UPDATED", saved.id());
        return enrichTicket(actor, branchId, saved, now);
    }

    public KitchenTicketDto startTicket(AccessPrincipal actor, UUID ticketId, KitchenTicketActionRequest req, CorrelationId corr) {
        BranchId branchId = requireAnyKitchenPermission(actor, PermissionCode.KITCHEN_TICKET_START, PermissionCode.KITCHEN_UPDATE);
        KitchenTicket ticket = loadLockedTicket(ticketId, actor, branchId, req.expectedVersion());
        KitchenTicketStatePolicy.requireTicketTransition(ticket.status(), KitchenTicketStatus.PREPARING);

        Instant now = clock.now();
        List<KitchenTicketItem> startedItems = ticket.items().stream()
                .map(i -> (i.status() == KitchenTicketItemStatus.NEW || i.status() == KitchenTicketItemStatus.QUEUED)
                        ? withItemStatus(i, KitchenTicketItemStatus.PREPARING, now, i.readyAt(), i.servedAt(), null)
                        : i)
                .toList();

        KitchenTicket saved = kitchenRepo.saveTicket(new KitchenTicket(
                ticket.id(), ticket.tenantId(), ticket.branchId(), ticket.orderId(),
                ticket.orderNumberSnapshot(), ticket.orderTypeSnapshot(), ticket.tableLabelSnapshot(),
                ticket.waiterId(), ticket.waiterNameSnapshot(), ticket.kitchenStationId(),
                ticket.sourceOutboxEventId(), ticket.ticketNumber(), ticket.ticketKind(),
                KitchenTicketStatus.PREPARING, ticket.priorityLevel(), ticket.priorityScore(),
                ticket.expectedPrepMinutes(), ticket.firedAt(),
                ticket.acceptedAt() != null ? ticket.acceptedAt() : now,
                ticket.startedAt() != null ? ticket.startedAt() : now,
                ticket.readyAt(), ticket.bumpedAt(), ticket.cancelledAt(),
                ticket.cancelledByUserId(), null, ticket.cancelReason(),
                ticket.version(), startedItems
        ));

        kitchenRepo.appendTicketEvent(new KitchenTicketEvent(
                UUID.randomUUID(), actor.tenantId(), branchId, saved.id(), null, actor.userId(),
                "STARTED", ticket.status().name(), "PREPARING", req.reason(), null, now
        ));
        var prepItems = saved.items().stream()
                .filter(i -> i.status() == KitchenTicketItemStatus.PREPARING)
                .map(i -> new InventoryPayloadCodecPort.OrderPrepItemSnapshot(
                        i.orderItemId(), i.id(), null, null, i.quantity(), List.of()))
                .toList();
        if (!prepItems.isEmpty()) {
            inventoryDispatch.dispatchKitchenPrepStarted(
                    actor.tenantId(), branchId, saved.orderId(), saved.id(), actor.userId(), prepItems);
        }
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.KITCHEN_TICKET_STARTED,
                "KitchenTicket", saved.id().toString(), null, corr);
        eventPublisher.publish(actor.tenantId(), branchId, "KITCHEN_TICKET_UPDATED", saved.id());
        return enrichTicket(actor, branchId, saved, now);
    }

    public KitchenTicketDto updateItemStatus(
            AccessPrincipal actor,
            UUID ticketId,
            UUID itemId,
            UpdateKitchenItemStatusRequest req,
            CorrelationId corr
    ) {
        KitchenTicketItemStatus targetStatus = KitchenTicketItemStatus.valueOf(req.status());
        PermissionCode needed = switch (targetStatus) {
            case READY -> PermissionCode.KITCHEN_TICKET_READY;
            case CANCELLED -> PermissionCode.KITCHEN_TICKET_CANCEL;
            case SERVED -> PermissionCode.KITCHEN_TICKET_SERVE;
            default -> PermissionCode.KITCHEN_TICKET_START;
        };
        BranchId branchId = requireAnyKitchenPermission(actor, needed, PermissionCode.KITCHEN_UPDATE);
        KitchenTicket ticket = loadLockedTicket(ticketId, actor, branchId, req.expectedVersion());

        KitchenTicketItem targetItem = ticket.items().stream()
                .filter(i -> i.id().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Kitchen ticket item not found"));
        KitchenTicketStatePolicy.requireItemTransition(targetItem.status(), targetStatus);

        Instant now = clock.now();
        KitchenTicketItem updatedItem = withItemStatus(
                targetItem,
                targetStatus,
                targetStatus == KitchenTicketItemStatus.PREPARING ? now : targetItem.startedAt(),
                targetStatus == KitchenTicketItemStatus.READY ? now : targetItem.readyAt(),
                targetStatus == KitchenTicketItemStatus.SERVED ? now : targetItem.servedAt(),
                targetStatus == KitchenTicketItemStatus.CANCELLED ? req.reason() : targetItem.cancelReason()
        );

        List<KitchenTicketItem> nextItems = ticket.items().stream()
                .map(i -> i.id().equals(itemId) ? updatedItem : i)
                .toList();

        KitchenTicketStatus nextTicketStatus = ticket.status();
        Instant startedAt = ticket.startedAt();
        Instant readyAt = ticket.readyAt();

        if (targetStatus == KitchenTicketItemStatus.PREPARING && startedAt == null) {
            startedAt = now;
            if (nextTicketStatus == KitchenTicketStatus.NEW || nextTicketStatus == KitchenTicketStatus.QUEUED
                    || nextTicketStatus == KitchenTicketStatus.ACCEPTED) {
                nextTicketStatus = KitchenTicketStatus.PREPARING;
            }
        }
        if (KitchenTicketStatePolicy.areAllActiveItemsCancelled(nextItems)) {
            nextTicketStatus = KitchenTicketStatus.CANCELLED;
        } else if (KitchenTicketStatePolicy.areAllActiveItemsReady(nextItems) && !ticket.status().isTerminal()) {
            KitchenTicketStatePolicy.requireTicketTransition(nextTicketStatus, KitchenTicketStatus.READY);
            nextTicketStatus = KitchenTicketStatus.READY;
            readyAt = now;
        }

        KitchenTicket saved = kitchenRepo.saveTicket(new KitchenTicket(
                ticket.id(), ticket.tenantId(), ticket.branchId(), ticket.orderId(),
                ticket.orderNumberSnapshot(), ticket.orderTypeSnapshot(), ticket.tableLabelSnapshot(),
                ticket.waiterId(), ticket.waiterNameSnapshot(), ticket.kitchenStationId(),
                ticket.sourceOutboxEventId(), ticket.ticketNumber(), ticket.ticketKind(),
                nextTicketStatus, ticket.priorityLevel(), ticket.priorityScore(),
                ticket.expectedPrepMinutes(), ticket.firedAt(),
                ticket.acceptedAt() != null ? ticket.acceptedAt() : now,
                startedAt, readyAt, ticket.bumpedAt(),
                nextTicketStatus == KitchenTicketStatus.CANCELLED ? now : ticket.cancelledAt(),
                nextTicketStatus == KitchenTicketStatus.CANCELLED ? actor.userId() : ticket.cancelledByUserId(),
                ticket.holdReason(), ticket.cancelReason(), ticket.version(), nextItems
        ));

        String evtType = targetStatus == KitchenTicketItemStatus.READY ? "ITEM_READY"
                : (targetStatus == KitchenTicketItemStatus.CANCELLED ? "ITEM_CANCELLED" : "STARTED");
        kitchenRepo.appendTicketEvent(new KitchenTicketEvent(
                UUID.randomUUID(), actor.tenantId(), branchId, saved.id(), itemId, actor.userId(),
                evtType, targetItem.status().name(), targetStatus.name(), req.reason(), null, now
        ));
        if (targetStatus == KitchenTicketItemStatus.PREPARING) {
            inventoryDispatch.dispatchKitchenPrepStarted(
                    actor.tenantId(), branchId, saved.orderId(), saved.id(), actor.userId(),
                    List.of(new InventoryPayloadCodecPort.OrderPrepItemSnapshot(
                            updatedItem.orderItemId(), updatedItem.id(), null, null, updatedItem.quantity(), List.of())));
        }
        if (nextTicketStatus == KitchenTicketStatus.READY && ticket.status() != KitchenTicketStatus.READY) {
            kitchenRepo.appendTicketEvent(new KitchenTicketEvent(
                    UUID.randomUUID(), actor.tenantId(), branchId, saved.id(), null, actor.userId(),
                    "READY", ticket.status().name(), "READY", "All active items ready", null, now
            ));
            auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.KITCHEN_TICKET_READY,
                    "KitchenTicket", saved.id().toString(), null, corr);
        }
        eventPublisher.publish(actor.tenantId(), branchId, "KITCHEN_TICKET_UPDATED", saved.id());
        return enrichTicket(actor, branchId, saved, now);
    }

    public KitchenTicketDto markTicketReady(AccessPrincipal actor, UUID ticketId, KitchenTicketActionRequest req, CorrelationId corr) {
        BranchId branchId = requireAnyKitchenPermission(actor, PermissionCode.KITCHEN_TICKET_READY, PermissionCode.KITCHEN_UPDATE);
        KitchenTicket ticket = loadLockedTicket(ticketId, actor, branchId, req.expectedVersion());
        KitchenTicketStatePolicy.requireTicketTransition(ticket.status(), KitchenTicketStatus.READY);

        Instant now = clock.now();
        List<KitchenTicketItem> readyItems = ticket.items().stream()
                .map(i -> i.status().isCancelledOrVoided() ? i : withItemStatus(
                        i, KitchenTicketItemStatus.READY,
                        i.startedAt() != null ? i.startedAt() : now, now, i.servedAt(), i.cancelReason()))
                .toList();

        KitchenTicket saved = kitchenRepo.saveTicket(new KitchenTicket(
                ticket.id(), ticket.tenantId(), ticket.branchId(), ticket.orderId(),
                ticket.orderNumberSnapshot(), ticket.orderTypeSnapshot(), ticket.tableLabelSnapshot(),
                ticket.waiterId(), ticket.waiterNameSnapshot(), ticket.kitchenStationId(),
                ticket.sourceOutboxEventId(), ticket.ticketNumber(), ticket.ticketKind(),
                KitchenTicketStatus.READY, ticket.priorityLevel(), ticket.priorityScore(),
                ticket.expectedPrepMinutes(), ticket.firedAt(),
                ticket.acceptedAt() != null ? ticket.acceptedAt() : now,
                ticket.startedAt() != null ? ticket.startedAt() : now,
                now, ticket.bumpedAt(), ticket.cancelledAt(), ticket.cancelledByUserId(),
                null, ticket.cancelReason(), ticket.version(), readyItems
        ));

        kitchenRepo.appendTicketEvent(new KitchenTicketEvent(
                UUID.randomUUID(), actor.tenantId(), branchId, saved.id(), null, actor.userId(),
                "READY", ticket.status().name(), "READY", req.reason(), null, now
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.KITCHEN_TICKET_READY,
                "KitchenTicket", saved.id().toString(), null, corr);
        eventPublisher.publish(actor.tenantId(), branchId, "KITCHEN_TICKET_UPDATED", saved.id());
        return enrichTicket(actor, branchId, saved, now);
    }

    public KitchenTicketDto serveTicket(AccessPrincipal actor, UUID ticketId, KitchenTicketActionRequest req, CorrelationId corr) {
        BranchId branchId = requireAnyKitchenPermission(actor, PermissionCode.KITCHEN_TICKET_SERVE, PermissionCode.KITCHEN_UPDATE);
        KitchenTicket ticket = loadLockedTicket(ticketId, actor, branchId, req.expectedVersion());
        KitchenTicketStatePolicy.requireTicketTransition(ticket.status(), KitchenTicketStatus.SERVED);

        Instant now = clock.now();
        List<KitchenTicketItem> servedItems = ticket.items().stream()
                .map(i -> i.status().isCancelledOrVoided() ? i : withItemStatus(
                        i, KitchenTicketItemStatus.SERVED, i.startedAt(), i.readyAt(), now, i.cancelReason()))
                .toList();

        KitchenTicket saved = kitchenRepo.saveTicket(new KitchenTicket(
                ticket.id(), ticket.tenantId(), ticket.branchId(), ticket.orderId(),
                ticket.orderNumberSnapshot(), ticket.orderTypeSnapshot(), ticket.tableLabelSnapshot(),
                ticket.waiterId(), ticket.waiterNameSnapshot(), ticket.kitchenStationId(),
                ticket.sourceOutboxEventId(), ticket.ticketNumber(), ticket.ticketKind(),
                KitchenTicketStatus.SERVED, ticket.priorityLevel(), ticket.priorityScore(),
                ticket.expectedPrepMinutes(), ticket.firedAt(), ticket.acceptedAt(),
                ticket.startedAt(), ticket.readyAt(), now, ticket.cancelledAt(),
                ticket.cancelledByUserId(), ticket.holdReason(), ticket.cancelReason(),
                ticket.version(), servedItems
        ));

        kitchenRepo.appendTicketEvent(new KitchenTicketEvent(
                UUID.randomUUID(), actor.tenantId(), branchId, saved.id(), null, actor.userId(),
                "SERVED", ticket.status().name(), "SERVED", req.reason(), null, now
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.KITCHEN_TICKET_SERVED,
                "KitchenTicket", saved.id().toString(), null, corr);
        eventPublisher.publish(actor.tenantId(), branchId, "KITCHEN_TICKET_UPDATED", saved.id());
        return enrichTicket(actor, branchId, saved, now);
    }

    public KitchenTicketDto holdTicket(AccessPrincipal actor, UUID ticketId, KitchenTicketActionRequest req, CorrelationId corr) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.KITCHEN_TICKET_HOLD);
        if (req.reason() == null || req.reason().isBlank()) {
            throw new DomainException("HOLD_REASON_REQUIRED", "A reason is required to place a kitchen ticket on hold");
        }
        KitchenTicket ticket = loadLockedTicket(ticketId, actor, branchId, req.expectedVersion());
        KitchenTicketStatePolicy.requireTicketTransition(ticket.status(), KitchenTicketStatus.ON_HOLD);

        Instant now = clock.now();
        KitchenTicket saved = kitchenRepo.saveTicket(new KitchenTicket(
                ticket.id(), ticket.tenantId(), ticket.branchId(), ticket.orderId(),
                ticket.orderNumberSnapshot(), ticket.orderTypeSnapshot(), ticket.tableLabelSnapshot(),
                ticket.waiterId(), ticket.waiterNameSnapshot(), ticket.kitchenStationId(),
                ticket.sourceOutboxEventId(), ticket.ticketNumber(), ticket.ticketKind(),
                KitchenTicketStatus.ON_HOLD, ticket.priorityLevel(), ticket.priorityScore(),
                ticket.expectedPrepMinutes(), ticket.firedAt(), ticket.acceptedAt(),
                ticket.startedAt(), ticket.readyAt(), ticket.bumpedAt(), ticket.cancelledAt(),
                ticket.cancelledByUserId(), req.reason().trim(), ticket.cancelReason(),
                ticket.version(), ticket.items()
        ));

        kitchenRepo.appendTicketEvent(new KitchenTicketEvent(
                UUID.randomUUID(), actor.tenantId(), branchId, saved.id(), null, actor.userId(),
                "HELD", ticket.status().name(), "ON_HOLD", req.reason().trim(), null, now
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.KITCHEN_TICKET_HELD,
                "KitchenTicket", saved.id().toString(), "{\"reason\":\"" + req.reason().trim() + "\"}", corr);
        eventPublisher.publish(actor.tenantId(), branchId, "KITCHEN_TICKET_UPDATED", saved.id());
        return enrichTicket(actor, branchId, saved, now);
    }

    public KitchenTicketDto releaseTicket(AccessPrincipal actor, UUID ticketId, KitchenTicketActionRequest req, CorrelationId corr) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.KITCHEN_TICKET_HOLD);
        KitchenTicket ticket = loadLockedTicket(ticketId, actor, branchId, req.expectedVersion());
        if (ticket.status() != KitchenTicketStatus.ON_HOLD) {
            throw new DomainException("TICKET_NOT_ON_HOLD", "Ticket is not currently ON_HOLD");
        }
        KitchenTicketStatus resumeStatus = ticket.startedAt() != null ? KitchenTicketStatus.PREPARING : KitchenTicketStatus.ACCEPTED;
        KitchenTicketStatePolicy.requireTicketTransition(ticket.status(), resumeStatus);

        Instant now = clock.now();
        KitchenTicket saved = kitchenRepo.saveTicket(new KitchenTicket(
                ticket.id(), ticket.tenantId(), ticket.branchId(), ticket.orderId(),
                ticket.orderNumberSnapshot(), ticket.orderTypeSnapshot(), ticket.tableLabelSnapshot(),
                ticket.waiterId(), ticket.waiterNameSnapshot(), ticket.kitchenStationId(),
                ticket.sourceOutboxEventId(), ticket.ticketNumber(), ticket.ticketKind(),
                resumeStatus, ticket.priorityLevel(), ticket.priorityScore(),
                ticket.expectedPrepMinutes(), ticket.firedAt(), ticket.acceptedAt(),
                ticket.startedAt(), ticket.readyAt(), ticket.bumpedAt(), ticket.cancelledAt(),
                ticket.cancelledByUserId(), null, ticket.cancelReason(),
                ticket.version(), ticket.items()
        ));

        kitchenRepo.appendTicketEvent(new KitchenTicketEvent(
                UUID.randomUUID(), actor.tenantId(), branchId, saved.id(), null, actor.userId(),
                "RELEASED", "ON_HOLD", resumeStatus.name(), req.reason(), null, now
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.KITCHEN_TICKET_RELEASED,
                "KitchenTicket", saved.id().toString(), null, corr);
        eventPublisher.publish(actor.tenantId(), branchId, "KITCHEN_TICKET_UPDATED", saved.id());
        return enrichTicket(actor, branchId, saved, now);
    }

    public KitchenTicketDto cancelTicket(AccessPrincipal actor, UUID ticketId, KitchenTicketActionRequest req, CorrelationId corr) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.KITCHEN_TICKET_CANCEL);
        if (req.reason() == null || req.reason().isBlank()) {
            throw new DomainException("CANCEL_REASON_REQUIRED", "Cancellation reason is required");
        }
        KitchenTicket ticket = loadLockedTicket(ticketId, actor, branchId, req.expectedVersion());
        KitchenTicketStatePolicy.requireTicketTransition(ticket.status(), KitchenTicketStatus.CANCELLED);

        Instant now = clock.now();
        List<KitchenTicketItem> cancelledItems = ticket.items().stream()
                .map(i -> i.status().isCancelledOrVoided() ? i : withItemStatus(
                        i, KitchenTicketItemStatus.CANCELLED, i.startedAt(), i.readyAt(), i.servedAt(), req.reason().trim()))
                .toList();

        KitchenTicket saved = kitchenRepo.saveTicket(new KitchenTicket(
                ticket.id(), ticket.tenantId(), ticket.branchId(), ticket.orderId(),
                ticket.orderNumberSnapshot(), ticket.orderTypeSnapshot(), ticket.tableLabelSnapshot(),
                ticket.waiterId(), ticket.waiterNameSnapshot(), ticket.kitchenStationId(),
                ticket.sourceOutboxEventId(), ticket.ticketNumber(), ticket.ticketKind(),
                KitchenTicketStatus.CANCELLED, ticket.priorityLevel(), ticket.priorityScore(),
                ticket.expectedPrepMinutes(), ticket.firedAt(), ticket.acceptedAt(),
                ticket.startedAt(), ticket.readyAt(), ticket.bumpedAt(), now, actor.userId(),
                ticket.holdReason(), req.reason().trim(), ticket.version(), cancelledItems
        ));

        kitchenRepo.appendTicketEvent(new KitchenTicketEvent(
                UUID.randomUUID(), actor.tenantId(), branchId, saved.id(), null, actor.userId(),
                "CANCELLED", ticket.status().name(), "CANCELLED", req.reason().trim(), null, now
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.KITCHEN_TICKET_CANCELLED,
                "KitchenTicket", saved.id().toString(), "{\"reason\":\"" + req.reason().trim() + "\"}", corr);
        eventPublisher.publish(actor.tenantId(), branchId, "KITCHEN_TICKET_UPDATED", saved.id());
        return enrichTicket(actor, branchId, saved, now);
    }

    public KitchenTicketDto refireTicket(AccessPrincipal actor, UUID ticketId, KitchenTicketActionRequest req, CorrelationId corr) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.KITCHEN_TICKET_REFIRE);
        if (req.reason() == null || req.reason().isBlank()) {
            throw new DomainException("REFIRE_REASON_REQUIRED", "Re-fire reason is required");
        }
        KitchenTicket original = loadLockedTicket(ticketId, actor, branchId, req.expectedVersion());
        KitchenStation station = kitchenRepo.findStationScoped(original.kitchenStationId(), actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found"));

        Instant now = clock.now();
        UUID refireTicketId = UUID.randomUUID();
        String refireNumber = original.ticketNumber() + "-RF" + refireTicketId.toString().substring(0, 3).toUpperCase();
        List<KitchenTicketItem> clonedItems = original.items().stream()
                .filter(i -> !i.status().isCancelledOrVoided())
                .map(i -> new KitchenTicketItem(
                        UUID.randomUUID(), actor.tenantId(), branchId, refireTicketId,
                        i.orderItemId(), i.itemNameSnapshot(), i.variantNameSnapshot(),
                        i.quantity(), i.selectedModifiers(), i.prepInstructions(), i.notes(),
                        i.expectedPrepMinutes(), KitchenTicketItemStatus.NEW,
                        null, null, null, null, 0L
                ))
                .toList();

        KitchenTicket savedRefire = kitchenRepo.saveTicket(new KitchenTicket(
                refireTicketId, actor.tenantId(), branchId, original.orderId(),
                original.orderNumberSnapshot(), original.orderTypeSnapshot(), original.tableLabelSnapshot(),
                original.waiterId(), original.waiterNameSnapshot(), original.kitchenStationId(),
                original.sourceOutboxEventId(), refireNumber, "REFIRE",
                KitchenTicketStatus.NEW, KitchenPriorityLevel.HIGH, KitchenPriorityLevel.HIGH.score(),
                original.expectedPrepMinutes(), now, null, null, null, null, null, null,
                null, null, 0L, clonedItems
        ));

        // Append auditable REFIRED event on BOTH original ticket (preserving original history) and FIRED on new ticket
        kitchenRepo.appendTicketEvent(new KitchenTicketEvent(
                UUID.randomUUID(), actor.tenantId(), branchId, original.id(), null, actor.userId(),
                "REFIRED", original.status().name(), original.status().name(),
                req.reason().trim(), "{\"refireTicketId\":\"" + savedRefire.id() + "\"}", now
        ));
        kitchenRepo.appendTicketEvent(new KitchenTicketEvent(
                UUID.randomUUID(), actor.tenantId(), branchId, savedRefire.id(), null, actor.userId(),
                "FIRED", null, "NEW", "Re-fire of " + original.ticketNumber() + ": " + req.reason().trim(),
                "{\"originalTicketId\":\"" + original.id() + "\"}", now
        ));

        printDispatcher.dispatchTicketPrint(savedRefire, station, "REFIRE", req.reason().trim(), actor.userId(), corr);
        var refireSnapshots = savedRefire.items().stream()
                .map(i -> new InventoryPayloadCodecPort.OrderPrepItemSnapshot(
                        i.orderItemId(), i.id(), null, null, i.quantity(), List.of()))
                .toList();
        inventoryDispatch.dispatchKitchenRefire(
                actor.tenantId(), branchId, original.orderId(), original.id(), savedRefire.id(),
                actor.userId(), req.reason().trim(), refireSnapshots);
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.KITCHEN_TICKET_REFIRED,
                "KitchenTicket", original.id().toString(),
                "{\"refireTicketId\":\"" + savedRefire.id() + "\",\"reason\":\"" + req.reason().trim() + "\"}", corr);
        eventPublisher.publish(actor.tenantId(), branchId, "KITCHEN_TICKET_CREATED", savedRefire.id());
        return toTicketDto(savedRefire, station, now);
    }

    public List<ExpoOrderDto> getExpoOrders(AccessPrincipal actor) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.KITCHEN_EXPO_VIEW);
        var cfg = kitchenRepo.findDisplayConfig(actor.tenantId(), branchId).orElse(null);
        if (cfg != null && !cfg.expoEnabled()) {
            throw new DomainException("EXPO_DISABLED", "Kitchen Expo display is disabled for this branch");
        }

        Instant now = clock.now();
        Map<UUID, KitchenStation> stations = kitchenRepo.findStationsByBranch(actor.tenantId(), branchId).stream()
                .collect(Collectors.toMap(KitchenStation::id, Function.identity()));

        Map<UUID, List<KitchenTicket>> byOrder = kitchenRepo.findActiveTicketsByBranch(actor.tenantId(), branchId).stream()
                .collect(Collectors.groupingBy(KitchenTicket::orderId, LinkedHashMap::new, Collectors.toList()));

        List<ExpoOrderDto> result = new ArrayList<>();
        for (Map.Entry<UUID, List<KitchenTicket>> entry : byOrder.entrySet()) {
            List<KitchenTicket> orderTickets = entry.getValue();
            KitchenTicket first = orderTickets.getFirst();
            boolean allReady = KitchenTicketStatePolicy.isExpoOrderReady(orderTickets);

            int readyCount = 0;
            int totalActiveCount = 0;
            List<String> pendingStations = new ArrayList<>();
            long maxElapsed = 0L;
            boolean anyOverdue = false;
            Instant earliestFired = first.firedAt();

            List<KitchenTicketDto> ticketDtos = new ArrayList<>();
            for (KitchenTicket t : orderTickets) {
                KitchenStation st = stations.get(t.kitchenStationId());
                KitchenTicketDto dto = toTicketDto(t, st, now);
                ticketDtos.add(dto);
                if (t.firedAt().isBefore(earliestFired)) {
                    earliestFired = t.firedAt();
                }
                if (t.status() != KitchenTicketStatus.CANCELLED) {
                    totalActiveCount++;
                    if (t.status() == KitchenTicketStatus.READY || t.status() == KitchenTicketStatus.SERVED
                            || t.status() == KitchenTicketStatus.BUMPED) {
                        readyCount++;
                    } else if (st != null) {
                        pendingStations.add(st.name() + " (" + t.status() + ")");
                    }
                }
                maxElapsed = Math.max(maxElapsed, dto.elapsedSeconds());
                anyOverdue = anyOverdue || dto.overdue();
            }

            if (totalActiveCount == 0) {
                continue;
            }
            String aggStatus = allReady ? "READY_FOR_PASS" : (readyCount > 0 ? "PARTIALLY_READY" : "IN_PREPARATION");
            result.add(new ExpoOrderDto(
                    entry.getKey(), first.orderNumberSnapshot(), first.orderTypeSnapshot(),
                    first.tableLabelSnapshot(), aggStatus, allReady, readyCount, totalActiveCount,
                    pendingStations, earliestFired, maxElapsed, anyOverdue, ticketDtos
            ));
        }
        return result;
    }

    public List<KitchenTicketDto> serveExpoOrder(AccessPrincipal actor, UUID orderId, CorrelationId corr) {
        BranchId branchId = requireAnyKitchenPermission(actor, PermissionCode.KITCHEN_TICKET_SERVE, PermissionCode.KITCHEN_UPDATE);
        List<KitchenTicket> orderTickets = kitchenRepo.findTicketsByOrderScoped(orderId, actor.tenantId(), branchId).stream()
                .filter(t -> t.status() != KitchenTicketStatus.CANCELLED)
                .toList();
        if (!KitchenTicketStatePolicy.isExpoOrderReady(orderTickets)) {
            throw new DomainException("EXPO_ORDER_NOT_READY",
                    "Cannot mark Expo order served while one or more stations are not READY");
        }
        List<KitchenTicketDto> served = new ArrayList<>();
        for (KitchenTicket t : orderTickets) {
            if (t.status() == KitchenTicketStatus.READY) {
                served.add(serveTicket(actor, t.id(), new KitchenTicketActionRequest(t.version(), "Expo pass served", null), corr));
            }
        }
        return served;
    }

    public List<KitchenPrintJobDto> listFailedPrintJobs(AccessPrincipal actor) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.KITCHEN_VIEW);
        return kitchenRepo.findFailedPrintJobs(actor.tenantId(), branchId).stream()
                .map(KitchenWorkflowService::toPrintJobDto)
                .toList();
    }

    public List<KitchenPrintJobDto> listTicketPrintJobs(AccessPrincipal actor, UUID ticketId) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.KITCHEN_VIEW);
        return kitchenRepo.findPrintJobsByTicket(ticketId, actor.tenantId(), branchId).stream()
                .map(KitchenWorkflowService::toPrintJobDto)
                .toList();
    }

    public KitchenPrintJobDto retryPrintJob(AccessPrincipal actor, UUID jobId, CorrelationId corr) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.KITCHEN_PRINTER_MANAGE);
        KitchenPrintJob job = kitchenRepo.findPrintJobScoped(jobId, actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Kitchen print job not found in branch"));
        KitchenPrintJob retried = printDispatcher.retryJob(job, actor.userId(), corr);
        return toPrintJobDto(retried);
    }

    private KitchenTicket loadLockedTicket(UUID ticketId, AccessPrincipal actor, BranchId branchId, long expectedVersion) {
        KitchenTicket ticket = kitchenRepo.findTicketScoped(ticketId, actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Kitchen ticket not found in branch"));
        if (ticket.version() != expectedVersion) {
            throw new ConcurrentModificationDomainException(
                    "Kitchen ticket " + ticket.ticketNumber() + " was modified by another screen (expected v"
                            + expectedVersion + ", actual v" + ticket.version() + ")",
                    null
            );
        }
        return ticket;
    }

    private KitchenTicketDto enrichTicket(AccessPrincipal actor, BranchId branchId, KitchenTicket ticket, Instant now) {
        KitchenStation station = kitchenRepo.findStationScoped(ticket.kitchenStationId(), actor.tenantId(), branchId).orElse(null);
        return toTicketDto(ticket, station, now);
    }

    public static KitchenTicketDto toTicketDto(KitchenTicket t, KitchenStation station, Instant now) {
        long elapsed = KitchenTicketStatePolicy.elapsedSeconds(t.firedAt(), now);
        boolean overdue = KitchenTicketStatePolicy.isOverdue(t.status(), t.firedAt(), t.expectedPrepMinutes(), now);
        List<KitchenTicketItemDto> itemDtos = t.items().stream()
                .map(i -> new KitchenTicketItemDto(
                        i.id(), i.kitchenTicketId(), i.orderItemId(), i.itemNameSnapshot(), i.variantNameSnapshot(),
                        i.quantity(), i.selectedModifiers(), i.prepInstructions(), i.notes(), i.expectedPrepMinutes(),
                        i.status().name(), i.startedAt(), i.readyAt(), i.servedAt(), i.cancelReason(), i.version()
                ))
                .toList();
        return new KitchenTicketDto(
                t.id(), t.branchId().value(), t.orderId(), t.orderNumberSnapshot(),
                t.orderTypeSnapshot(), t.tableLabelSnapshot(), t.waiterId(), t.waiterNameSnapshot(),
                t.kitchenStationId(), station != null ? station.code() : "UNKNOWN", station != null ? station.name() : "Unknown",
                t.ticketNumber(), t.ticketKind(), t.status().name(), t.priorityLevel().name(), t.priorityScore(),
                t.expectedPrepMinutes(), elapsed, overdue, t.firedAt(), t.acceptedAt(), t.startedAt(),
                t.readyAt(), t.bumpedAt(), t.cancelledAt(), t.holdReason(), t.cancelReason(), t.version(), itemDtos
        );
    }

    private static List<KitchenTicketDto> sortTickets(List<KitchenTicketDto> dtos, String sortMode) {
        Comparator<KitchenTicketDto> comp = switch (sortMode != null ? sortMode.toUpperCase() : "FIFO") {
            case "PRIORITY" -> Comparator.comparingInt(KitchenTicketDto::priorityScore).reversed()
                    .thenComparing(KitchenTicketDto::firedAt);
            case "OVERDUE_FIRST" -> Comparator.comparing(KitchenTicketDto::overdue).reversed()
                    .thenComparingInt(KitchenTicketDto::priorityScore).reversed()
                    .thenComparing(KitchenTicketDto::firedAt);
            default -> Comparator.comparing(KitchenTicketDto::firedAt);
        };
        return dtos.stream().sorted(comp).toList();
    }

    private static KitchenTicketItem withItemStatus(
            KitchenTicketItem i,
            KitchenTicketItemStatus nextStatus,
            Instant startedAt,
            Instant readyAt,
            Instant servedAt,
            String cancelReason
    ) {
        return new KitchenTicketItem(
                i.id(), i.tenantId(), i.branchId(), i.kitchenTicketId(), i.orderItemId(),
                i.itemNameSnapshot(), i.variantNameSnapshot(), i.quantity(), i.selectedModifiers(),
                i.prepInstructions(), i.notes(), i.expectedPrepMinutes(), nextStatus,
                startedAt, readyAt, servedAt, cancelReason, i.version()
        );
    }

    private static KitchenPrintJobDto toPrintJobDto(KitchenPrintJob j) {
        return new KitchenPrintJobDto(
                j.id(), j.kitchenTicketId(), j.kitchenStationId(), j.jobType(), j.printerName(),
                j.language(), j.paperWidth(), j.renderedText(), j.status(), j.attemptCount(),
                j.errorMessage(), j.createdAt(), j.lastAttemptAt()
        );
    }

    private static BranchId requireBranchPermission(AccessPrincipal actor, PermissionCode perm) {
        if (actor == null || actor.activeBranchId() == null) {
            throw new UnauthorizedOperationException(perm, "Active branch session required");
        }
        PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), perm);
        return actor.activeBranchId();
    }

    private static BranchId requireAnyKitchenPermission(AccessPrincipal actor, PermissionCode primary, PermissionCode fallback) {
        if (actor == null || actor.activeBranchId() == null) {
            throw new UnauthorizedOperationException(primary, "Active branch session required");
        }
        if (actor.hasPermission(primary)) {
            PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), primary);
        } else {
            PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), fallback);
        }
        return actor.activeBranchId();
    }
}
