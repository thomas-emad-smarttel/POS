package com.restaurantpos.application.kitchen;

import com.restaurantpos.application.audit.AuditRecordingService;
import com.restaurantpos.domain.kitchen.KitchenDisplayConfiguration;
import com.restaurantpos.domain.kitchen.KitchenPriorityLevel;
import com.restaurantpos.domain.kitchen.KitchenRoutingEngine;
import com.restaurantpos.domain.kitchen.KitchenRoutingRule;
import com.restaurantpos.domain.kitchen.KitchenStation;
import com.restaurantpos.domain.kitchen.KitchenTicket;
import com.restaurantpos.domain.kitchen.KitchenTicketEvent;
import com.restaurantpos.domain.kitchen.KitchenTicketItem;
import com.restaurantpos.domain.kitchen.KitchenTicketItemStatus;
import com.restaurantpos.domain.kitchen.KitchenTicketStatus;
import com.restaurantpos.domain.kitchen.KitchenUnroutedItem;
import com.restaurantpos.domain.kitchen.OutboxWorkItem;
import com.restaurantpos.domain.port.BranchEventPublishPort;
import com.restaurantpos.domain.port.KitchenPayloadCodecPort;
import com.restaurantpos.domain.port.KitchenRepositoryPort;
import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.shared.security.AuditAction;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reliable, idempotent consumer of kitchen outbox events (`ORDER_SENT_TO_KITCHEN`, `ORDER_CANCELLED_IN_KITCHEN`).
 * Tracks processed event IDs, routes items deterministically across stations, captures unrouted items,
 * schedules retries on failure, triggers automatic station ESC/POS prints without rolling back tickets,
 * and broadcasts WebSocket branch invalidation notices after creation.
 */
public class KitchenOutboxConsumerService {

    private final KitchenRepositoryPort kitchenRepo;
    private final KitchenPayloadCodecPort codec;
    private final KitchenPrintDispatcher printDispatcher;
    private final BranchEventPublishPort eventPublisher;
    private final AuditRecordingService auditService;
    private final SystemClockPort clock;

    public KitchenOutboxConsumerService(
            KitchenRepositoryPort kitchenRepo,
            KitchenPayloadCodecPort codec,
            KitchenPrintDispatcher printDispatcher,
            BranchEventPublishPort eventPublisher,
            AuditRecordingService auditService,
            SystemClockPort clock
    ) {
        this.kitchenRepo = kitchenRepo;
        this.codec = codec;
        this.printDispatcher = printDispatcher;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.clock = clock;
    }

    public int processPendingEvents(int batchSize) {
        Instant now = clock.now();
        List<OutboxWorkItem> batch = kitchenRepo.claimPendingKitchenOutboxEvents(batchSize, now);
        int processed = 0;
        for (OutboxWorkItem event : batch) {
            try {
                processSingleEvent(event);
                processed++;
            } catch (Exception ex) {
                int nextRetry = event.retryCount() + 1;
                String nextStatus = nextRetry >= 5 ? "DEAD_LETTER" : "PENDING";
                Instant nextAttempt = now.plus(Math.min(60, 2L * nextRetry), ChronoUnit.SECONDS);
                kitchenRepo.markOutboxEventRetry(event.id(), nextRetry, nextAttempt, nextStatus);
            }
        }
        return processed;
    }

    public void processSingleEvent(OutboxWorkItem event) {
        if (kitchenRepo.isOutboxEventProcessed(event.id())) {
            kitchenRepo.markOutboxEventPublished(event.id(), clock.now());
            return;
        }
        if (event.branchId() == null) {
            kitchenRepo.markOutboxEventProcessed(event.id(), event.tenantId(), null, event.eventType(), clock.now());
            kitchenRepo.markOutboxEventPublished(event.id(), clock.now());
            return;
        }

        switch (event.eventType()) {
            case "ORDER_SENT_TO_KITCHEN" -> handleOrderSent(event);
            case "ORDER_CANCELLED_IN_KITCHEN" -> handleOrderCancelled(event);
            default -> {
                kitchenRepo.markOutboxEventProcessed(event.id(), event.tenantId(), event.branchId(), event.eventType(), clock.now());
                kitchenRepo.markOutboxEventPublished(event.id(), clock.now());
            }
        }
    }

    private void handleOrderSent(OutboxWorkItem event) {
        TenantId tenantId = event.tenantId();
        BranchId branchId = event.branchId();
        Instant now = clock.now();
        CorrelationId corr = CorrelationId.of("outbox-" + event.id().toString().substring(0, 8));

        KitchenPayloadCodecPort.DispatchedOrderSnapshot snap = codec.decodeOrderDispatched(event.payloadJson());
        List<KitchenStation> activeStations = kitchenRepo.findStationsByBranch(tenantId, branchId).stream()
                .filter(KitchenStation::active)
                .toList();
        List<KitchenRoutingRule> rules = kitchenRepo.findRoutingRulesByBranch(tenantId, branchId);
        KitchenDisplayConfiguration displayCfg = kitchenRepo.findDisplayConfig(tenantId, branchId).orElse(null);
        int defaultPrepMin = displayCfg != null ? displayCfg.defaultExpectedPrepMinutes() : 10;
        boolean autoPrint = displayCfg == null || displayCfg.autoPrintOnDispatch();

        Set<UUID> alreadyDispatchedItems = kitchenRepo.findDispatchedOrderItemIdsForOrder(snap.orderId(), tenantId, branchId);
        boolean isSupplemental = !alreadyDispatchedItems.isEmpty();

        Map<UUID, KitchenStation> stationById = new LinkedHashMap<>();
        Map<UUID, List<ResolvedStationItem>> itemsByStation = new LinkedHashMap<>();

        for (KitchenPayloadCodecPort.DispatchedItemSnapshot itemSnap : snap.items()) {
            if (alreadyDispatchedItems.contains(itemSnap.orderItemId())) {
                continue; // Incremental dispatch: never duplicate previously ticketed order items
            }

            List<KitchenRoutingEngine.RoutingTarget> targets = KitchenRoutingEngine.resolveStationsForItem(
                    activeStations, rules, defaultPrepMin, itemSnap.productId(), itemSnap.categoryId()
            );

            if (targets.isEmpty()) {
                kitchenRepo.saveUnroutedItem(new KitchenUnroutedItem(
                        UUID.randomUUID(), tenantId, branchId, event.id(), snap.orderId(), snap.orderNumber(),
                        itemSnap.orderItemId(), itemSnap.productId(), itemSnap.productName(), itemSnap.quantity(),
                        itemSnap.modifiers() != null ? String.join(", ", itemSnap.modifiers()) : null,
                        "OPEN", null, null, null, now
                ));
                eventPublisher.publish(tenantId, branchId, "KITCHEN_UNROUTED_ITEM", itemSnap.orderItemId());
                continue;
            }

            for (KitchenRoutingEngine.RoutingTarget target : targets) {
                stationById.put(target.station().id(), target.station());
                itemsByStation.computeIfAbsent(target.station().id(), k -> new ArrayList<>())
                        .add(new ResolvedStationItem(itemSnap, target.expectedPrepMinutes()));
            }
        }

        List<KitchenTicket> createdTickets = new ArrayList<>();
        for (Map.Entry<UUID, List<ResolvedStationItem>> entry : itemsByStation.entrySet()) {
            KitchenStation station = stationById.get(entry.getKey());
            UUID ticketId = UUID.randomUUID();
            int maxPrep = entry.getValue().stream().mapToInt(ResolvedStationItem::prepMinutes).max().orElse(defaultPrepMin);
            String ticketNumber = "K-" + snap.orderNumber().replace("ORD-", "")
                    + "-" + station.code() + "-" + ticketId.toString().substring(0, 4).toUpperCase();
            String kind = isSupplemental ? "SUPPLEMENTAL" : "INITIAL";

            List<KitchenTicketItem> ticketItems = entry.getValue().stream()
                    .map(rsi -> new KitchenTicketItem(
                            UUID.randomUUID(), tenantId, branchId, ticketId,
                            rsi.snapshot().orderItemId(),
                            rsi.snapshot().variantName() != null
                                    ? rsi.snapshot().productName() + " (" + rsi.snapshot().variantName() + ")"
                                    : rsi.snapshot().productName(),
                            rsi.snapshot().variantName(),
                            rsi.snapshot().quantity(),
                            rsi.snapshot().modifiers() != null ? rsi.snapshot().modifiers() : List.of(),
                            rsi.snapshot().prepInstructions(),
                            rsi.snapshot().kitchenNotes(),
                            rsi.prepMinutes(),
                            KitchenTicketItemStatus.NEW,
                            null, null, null, null, 0L
                    ))
                    .toList();

            KitchenTicket saved = kitchenRepo.saveTicket(new KitchenTicket(
                    ticketId, tenantId, branchId, snap.orderId(), snap.orderNumber(),
                    snap.orderType(), snap.tableLabel(), snap.waiterId(), snap.waiterName(),
                    station.id(), event.id(), ticketNumber, kind,
                    KitchenTicketStatus.NEW, KitchenPriorityLevel.NORMAL, 0, maxPrep,
                    now, null, null, null, null, null, null, null, null, 0L, ticketItems
            ));

            kitchenRepo.appendTicketEvent(new KitchenTicketEvent(
                    UUID.randomUUID(), tenantId, branchId, saved.id(), null, snap.waiterId(),
                    "FIRED", null, "NEW", isSupplemental ? "Supplemental order items fired" : "Order fired to kitchen",
                    null, now
            ));

            auditService.record(tenantId, branchId, snap.waiterId(), AuditAction.KITCHEN_TICKET_CREATED,
                    "KitchenTicket", saved.id().toString(),
                    "{\"station\":\"" + station.code() + "\",\"kind\":\"" + kind + "\"}", corr);

            createdTickets.add(saved);
            if (autoPrint) {
                printDispatcher.dispatchTicketPrint(saved, station, kind, null, snap.waiterId(), corr);
            }
        }

        kitchenRepo.markOutboxEventProcessed(event.id(), tenantId, branchId, event.eventType(), now);
        kitchenRepo.markOutboxEventPublished(event.id(), now);

        for (KitchenTicket t : createdTickets) {
            eventPublisher.publish(tenantId, branchId, "KITCHEN_TICKET_CREATED", t.id());
        }
    }

    private void handleOrderCancelled(OutboxWorkItem event) {
        TenantId tenantId = event.tenantId();
        BranchId branchId = event.branchId();
        Instant now = clock.now();
        CorrelationId corr = CorrelationId.of("outbox-cxl-" + event.id().toString().substring(0, 8));
        KitchenPayloadCodecPort.CancelledOrderSnapshot snap = codec.decodeOrderCancelled(event.payloadJson());

        Map<UUID, KitchenStation> stations = kitchenRepo.findStationsByBranch(tenantId, branchId).stream()
                .collect(LinkedHashMap::new, (m, s) -> m.put(s.id(), s), Map::putAll);

        List<KitchenTicket> orderTickets = kitchenRepo.findTicketsByOrderScoped(snap.orderId(), tenantId, branchId);
        for (KitchenTicket ticket : orderTickets) {
            if (ticket.status().isTerminal()) {
                continue;
            }
            List<KitchenTicketItem> cancelledItems = ticket.items().stream()
                    .map(i -> i.status().isCancelledOrVoided() ? i : new KitchenTicketItem(
                            i.id(), i.tenantId(), i.branchId(), i.kitchenTicketId(), i.orderItemId(),
                            i.itemNameSnapshot(), i.variantNameSnapshot(), i.quantity(), i.selectedModifiers(),
                            i.prepInstructions(), i.notes(), i.expectedPrepMinutes(),
                            KitchenTicketItemStatus.CANCELLED, i.startedAt(), i.readyAt(), i.servedAt(),
                            snap.reason(), i.version()
                    ))
                    .toList();

            KitchenTicket saved = kitchenRepo.saveTicket(new KitchenTicket(
                    ticket.id(), ticket.tenantId(), ticket.branchId(), ticket.orderId(),
                    ticket.orderNumberSnapshot(), ticket.orderTypeSnapshot(), ticket.tableLabelSnapshot(),
                    ticket.waiterId(), ticket.waiterNameSnapshot(), ticket.kitchenStationId(),
                    ticket.sourceOutboxEventId(), ticket.ticketNumber(), ticket.ticketKind(),
                    KitchenTicketStatus.CANCELLED, ticket.priorityLevel(), ticket.priorityScore(),
                    ticket.expectedPrepMinutes(), ticket.firedAt(), ticket.acceptedAt(),
                    ticket.startedAt(), ticket.readyAt(), ticket.bumpedAt(), now, null,
                    ticket.holdReason(), snap.reason(), ticket.version(), cancelledItems
            ));
            kitchenRepo.appendTicketEvent(new KitchenTicketEvent(
                    UUID.randomUUID(), tenantId, branchId, saved.id(), null, null,
                    "CANCELLED", ticket.status().name(), "CANCELLED", snap.reason(), null, now
            ));
            KitchenStation station = stations.get(saved.kitchenStationId());
            if (station != null) {
                printDispatcher.dispatchTicketPrint(saved, station, "CANCELLED", snap.reason(), null, corr);
            }
            eventPublisher.publish(tenantId, branchId, "KITCHEN_TICKET_UPDATED", saved.id());
        }

        kitchenRepo.markOutboxEventProcessed(event.id(), tenantId, branchId, event.eventType(), now);
        kitchenRepo.markOutboxEventPublished(event.id(), now);
    }

    private record ResolvedStationItem(KitchenPayloadCodecPort.DispatchedItemSnapshot snapshot, int prepMinutes) {
    }
}
