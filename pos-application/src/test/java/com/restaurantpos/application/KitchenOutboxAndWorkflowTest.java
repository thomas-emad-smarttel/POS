package com.restaurantpos.application;

import com.restaurantpos.application.audit.AuditRecordingService;
import com.restaurantpos.application.kitchen.KitchenOutboxConsumerService;
import com.restaurantpos.application.kitchen.KitchenPrintDispatcher;
import com.restaurantpos.application.kitchen.KitchenWorkflowService;
import com.restaurantpos.domain.exception.ConcurrentModificationDomainException;
import com.restaurantpos.domain.exception.UnauthorizedOperationException;
import com.restaurantpos.domain.kitchen.KitchenDisplayConfiguration;
import com.restaurantpos.domain.kitchen.KitchenPrintJob;
import com.restaurantpos.domain.kitchen.KitchenPriorityLevel;
import com.restaurantpos.domain.kitchen.KitchenRoutingRule;
import com.restaurantpos.domain.kitchen.KitchenStation;
import com.restaurantpos.domain.kitchen.KitchenTicket;
import com.restaurantpos.domain.kitchen.KitchenTicketItem;
import com.restaurantpos.domain.kitchen.KitchenTicketItemStatus;
import com.restaurantpos.domain.kitchen.KitchenTicketStatus;
import com.restaurantpos.domain.kitchen.KitchenUnroutedItem;
import com.restaurantpos.domain.kitchen.OutboxWorkItem;
import com.restaurantpos.domain.port.BranchEventPublishPort;
import com.restaurantpos.domain.port.KitchenPayloadCodecPort;
import com.restaurantpos.domain.port.KitchenPayloadCodecPort.DispatchedItemSnapshot;
import com.restaurantpos.domain.port.KitchenPayloadCodecPort.DispatchedOrderSnapshot;
import com.restaurantpos.domain.port.KitchenPrinterPort;
import com.restaurantpos.domain.port.KitchenRepositoryPort;
import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.shared.dto.kitchen.KitchenTicketActionRequest;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.shared.security.PermissionCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KitchenOutboxAndWorkflowTest {

    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final BranchId BRANCH = BranchId.of(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    @Mock private KitchenRepositoryPort kitchenRepo;
    @Mock private KitchenPayloadCodecPort codec;
    @Mock private KitchenPrinterPort printerPort;
    @Mock private BranchEventPublishPort eventPublisher;

    private AuditRecordingService auditService;
    private SystemClockPort clock;
    private KitchenPrintDispatcher printDispatcher;
    private KitchenOutboxConsumerService outboxConsumer;
    private KitchenWorkflowService workflowService;

    @BeforeEach
    void setUp() {
        clock = () -> NOW;
        auditService = new AuditRecordingService(entry -> {}, clock);
        printDispatcher = new KitchenPrintDispatcher(kitchenRepo, printerPort, auditService, clock);
        outboxConsumer = new KitchenOutboxConsumerService(
                kitchenRepo, codec, printDispatcher, eventPublisher, auditService, clock);
        workflowService = new KitchenWorkflowService(
                kitchenRepo, printDispatcher, eventPublisher, auditService, clock);
        lenient().when(kitchenRepo.savePrintJob(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(kitchenRepo.saveTicket(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(kitchenRepo.appendTicketEvent(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void duplicateOutboxEventIsIdempotentlySkippedWithoutCreatingTickets() {
        UUID eventId = UUID.randomUUID();
        OutboxWorkItem work = new OutboxWorkItem(
                eventId, TENANT, BRANCH, "ORDER", UUID.randomUUID(),
                "ORDER_SENT_TO_KITCHEN", "k-1", "{}", "PENDING", 0, NOW, NOW);

        when(kitchenRepo.isOutboxEventProcessed(eventId)).thenReturn(true);

        outboxConsumer.processSingleEvent(work);

        verify(kitchenRepo).markOutboxEventPublished(eventId, NOW);
        verify(kitchenRepo, never()).saveTicket(any());
        verify(codec, never()).decodeOrderDispatched(any());
    }

    @Test
    void outboxProcessingFailureSchedulesRetryAndMovesToDeadLetterAfterFiveAttempts() {
        UUID eventId = UUID.randomUUID();
        OutboxWorkItem fifthAttempt = new OutboxWorkItem(
                eventId, TENANT, BRANCH, "ORDER", UUID.randomUUID(),
                "ORDER_SENT_TO_KITCHEN", "k-fail", "{bad-json", "PENDING", 4, NOW, NOW);

        when(kitchenRepo.claimPendingKitchenOutboxEvents(10, NOW)).thenReturn(List.of(fifthAttempt));
        when(kitchenRepo.isOutboxEventProcessed(eventId)).thenReturn(false);
        when(codec.decodeOrderDispatched("{bad-json")).thenThrow(new IllegalStateException("Malformed payload"));

        int processed = outboxConsumer.processPendingEvents(10);

        assertEquals(0, processed);
        verify(kitchenRepo).markOutboxEventRetry(eq(eventId), eq(5), any(Instant.class), eq("DEAD_LETTER"));
    }

    @Test
    void unroutedItemsAndPrinterUnavailabilityDoNotPreventTicketCreation() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID grillId = UUID.randomUUID();
        UUID burgerProd = UUID.randomUUID();
        UUID unmappedProd = UUID.randomUUID();

        KitchenStation grill = new KitchenStation(
                grillId, TENANT, BRANCH, "GRILL", "Hot Grill", "HOT_KITCHEN",
                null, 1, 10, true, 42, "EN", "UTF-8", false, true, 0L);

        OutboxWorkItem work = new OutboxWorkItem(
                eventId, TENANT, BRANCH, "ORDER", orderId,
                "ORDER_SENT_TO_KITCHEN", "k-2", "{valid}", "PENDING", 0, NOW, NOW);

        when(kitchenRepo.claimPendingKitchenOutboxEvents(anyInt(), any())).thenReturn(List.of(work));
        when(kitchenRepo.isOutboxEventProcessed(eventId)).thenReturn(false);
        when(kitchenRepo.findStationsByBranch(TENANT, BRANCH)).thenReturn(List.of(grill));
        when(kitchenRepo.findRoutingRulesByBranch(TENANT, BRANCH)).thenReturn(List.of(
                new KitchenRoutingRule(UUID.randomUUID(), TENANT, BRANCH, grillId, burgerProd, null, 1, null, true, 0L)
        ));
        when(kitchenRepo.findDisplayConfig(TENANT, BRANCH)).thenReturn(Optional.of(
                new KitchenDisplayConfiguration(UUID.randomUUID(), TENANT, BRANCH, true, "FIFO", 10, true, 0L)
        ));
        when(kitchenRepo.findDispatchedOrderItemIdsForOrder(orderId, TENANT, BRANCH)).thenReturn(Set.of());
        when(codec.decodeOrderDispatched("{valid}")).thenReturn(new DispatchedOrderSnapshot(
                orderId, "ORD-100", "DINE_IN", "T2", UUID.randomUUID(), "Bob",
                List.of(
                        new DispatchedItemSnapshot(UUID.randomUUID(), burgerProd, null, "Burger", null, BigDecimal.ONE, List.of(), null, null),
                        new DispatchedItemSnapshot(UUID.randomUUID(), unmappedProd, null, "Mystery Soup", null, BigDecimal.ONE, List.of(), null, null)
                )
        ));
        when(kitchenRepo.findPrintJobByIdempotency(eq(TENANT), eq(BRANCH), any())).thenReturn(Optional.empty());
        when(printerPort.renderAndPrint(any())).thenReturn(new KitchenPrinterPort.PrintOutcome(
                "Virtual-Kitchen", "RAW", "", "PRINTER_UNAVAILABLE", "Printer offline"));

        int count = outboxConsumer.processPendingEvents(10);
        assertEquals(1, count);

        // Ticket still saved + failed print job recorded + unrouted soup captured
        verify(kitchenRepo).saveTicket(any(KitchenTicket.class));
        ArgumentCaptor<KitchenPrintJob> printCaptor = ArgumentCaptor.forClass(KitchenPrintJob.class);
        verify(kitchenRepo).savePrintJob(printCaptor.capture());
        assertEquals("PRINTER_UNAVAILABLE", printCaptor.getValue().status());

        ArgumentCaptor<KitchenUnroutedItem> unroutedCaptor = ArgumentCaptor.forClass(KitchenUnroutedItem.class);
        verify(kitchenRepo).saveUnroutedItem(unroutedCaptor.capture());
        assertEquals("Mystery Soup", unroutedCaptor.getValue().productNameSnapshot());
        assertEquals("OPEN", unroutedCaptor.getValue().status());

        verify(kitchenRepo).markOutboxEventProcessed(eventId, TENANT, BRANCH, "ORDER_SENT_TO_KITCHEN", NOW);
        verify(kitchenRepo).markOutboxEventPublished(eventId, NOW);
    }

    @Test
    void staleVersionThrowsConcurrentModificationAndMissingPermissionThrowsUnauthorized() {
        UUID stationId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        KitchenTicket current = new KitchenTicket(
                ticketId, TENANT, BRANCH, UUID.randomUUID(), "ORD-1", "DINE_IN", "T1",
                UUID.randomUUID(), "Waiter", stationId, UUID.randomUUID(), "KT-01", "INITIAL",
                KitchenTicketStatus.QUEUED, KitchenPriorityLevel.NORMAL, 0, 10, NOW,
                null, null, null, null, null, null, null, null, 2L,
                List.of(new KitchenTicketItem(UUID.randomUUID(), TENANT, BRANCH, ticketId, UUID.randomUUID(),
                        "Fries", null, BigDecimal.ONE, List.of(), null, null, 10,
                        KitchenTicketItemStatus.QUEUED, null, null, null, null, 0L))
        );

        AccessPrincipal cook = new AccessPrincipal(
                UUID.randomUUID(), "cook1", TENANT, BRANCH, null, null, Set.of(BRANCH), Set.of("KITCHEN_STAFF"),
                Set.of(PermissionCode.KITCHEN_VIEW, PermissionCode.KITCHEN_TICKET_ACCEPT), true);

        when(kitchenRepo.findTicketScoped(ticketId, TENANT, BRANCH)).thenReturn(Optional.of(current));

        // Stale expectedVersion = 1L while ticket version = 2L -> optimistic conflict
        assertThrows(ConcurrentModificationDomainException.class, () ->
                workflowService.acceptTicket(cook, ticketId,
                        new KitchenTicketActionRequest(1L, null, null), CorrelationId.of("corr-1")));

        // Cook without KITCHEN_TICKET_CANCEL cannot cancel
        assertThrows(UnauthorizedOperationException.class, () ->
                workflowService.cancelTicket(cook, ticketId,
                        new KitchenTicketActionRequest(2L, "Customer left", null), CorrelationId.of("corr-2")));
    }
}
