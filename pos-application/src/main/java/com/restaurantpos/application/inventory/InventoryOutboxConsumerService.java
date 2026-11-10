package com.restaurantpos.application.inventory;

import com.restaurantpos.domain.kitchen.OutboxWorkItem;
import com.restaurantpos.domain.port.InventoryPayloadCodecPort;
import com.restaurantpos.domain.port.InventoryRepositoryPort;
import com.restaurantpos.domain.port.KitchenPayloadCodecPort;
import com.restaurantpos.domain.port.SystemClockPort;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Reliable, idempotent outbox consumer driving automated inventory reservation, prep consumption,
 * pre-prep release on order cancellation, and kitchen re-fire deductions.
 */
public class InventoryOutboxConsumerService {

    private final InventoryRepositoryPort inventoryRepo;
    private final KitchenPayloadCodecPort kitchenCodec;
    private final InventoryPayloadCodecPort inventoryCodec;
    private final StockLedgerService ledgerService;
    private final SystemClockPort clock;

    public InventoryOutboxConsumerService(
            InventoryRepositoryPort inventoryRepo,
            KitchenPayloadCodecPort kitchenCodec,
            InventoryPayloadCodecPort inventoryCodec,
            StockLedgerService ledgerService,
            SystemClockPort clock
    ) {
        this.inventoryRepo = inventoryRepo;
        this.kitchenCodec = kitchenCodec;
        this.inventoryCodec = inventoryCodec;
        this.ledgerService = ledgerService;
        this.clock = clock;
    }

    public int processPendingEvents(int batchSize) {
        Instant now = clock.now();
        List<OutboxWorkItem> batch = inventoryRepo.claimPendingInventoryOutboxEvents(batchSize, now);
        int processed = 0;
        for (OutboxWorkItem event : batch) {
            try {
                processSingleEvent(event);
                processed++;
            } catch (Exception ex) {
                int nextRetry = event.retryCount() + 1;
                String nextStatus = nextRetry >= 5 ? "DEAD_LETTER" : "PENDING";
                Instant nextAttempt = now.plus(Math.min(60, 2L * nextRetry), ChronoUnit.SECONDS);
                inventoryRepo.markOutboxEventRetry(event.id(), nextRetry, nextAttempt, nextStatus);
            }
        }
        return processed;
    }

    public void processSingleEvent(OutboxWorkItem event) {
        Instant now = clock.now();
        if (inventoryRepo.isOutboxEventProcessed(event.id())) {
            inventoryRepo.markOutboxEventPublished(event.id(), now);
            return;
        }
        if (event.branchId() == null) {
            inventoryRepo.markOutboxEventProcessed(event.id(), event.tenantId(), null, event.eventType(), now);
            inventoryRepo.markOutboxEventPublished(event.id(), now);
            return;
        }

        switch (event.eventType()) {
            case "INVENTORY_RESERVE_ORDER" -> {
                InventoryPayloadCodecPort.ReserveOrderPayload snap = inventoryCodec.decodeReserveOrder(event.payloadJson());
                ledgerService.reserveForOrder(event.tenantId(), event.branchId(), event.id(), snap);
            }
            case "INVENTORY_CONSUME_PREP" -> {
                InventoryPayloadCodecPort.PrepStartedPayload payload = inventoryCodec.decodePrepStarted(event.payloadJson());
                ledgerService.consumeForPrep(event.tenantId(), event.branchId(), event.id(), payload);
            }
            case "INVENTORY_RELEASE_ORDER" -> {
                KitchenPayloadCodecPort.CancelledOrderSnapshot snap = kitchenCodec.decodeOrderCancelled(event.payloadJson());
                ledgerService.releaseOrderReservation(event.tenantId(), event.branchId(), event.id(), snap.orderId(), snap.reason());
            }
            case "INVENTORY_REFIRE_TICKET" -> {
                InventoryPayloadCodecPort.TicketRefirePayload payload = inventoryCodec.decodeTicketRefire(event.payloadJson());
                ledgerService.consumeForRefire(event.tenantId(), event.branchId(), event.id(), payload);
            }
            default -> {
                // Unknown event type acknowledged idempotently
            }
        }

        inventoryRepo.markOutboxEventProcessed(event.id(), event.tenantId(), event.branchId(), event.eventType(), now);
        inventoryRepo.markOutboxEventPublished(event.id(), now);
    }
}
