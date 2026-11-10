package com.restaurantpos.domain.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Pure JSON codec port for inventory outbox events (`INVENTORY_PREP_STARTED`, `INVENTORY_TICKET_REFIRED`,
 * `INVENTORY_RESERVE_ORDER`, `INVENTORY_RELEASE_ORDER`, and recipe snapshots).
 */
public interface InventoryPayloadCodecPort {

    record OrderPrepItemSnapshot(
            UUID orderItemId,
            UUID kitchenTicketItemId,
            UUID productId,
            UUID productVariantId,
            BigDecimal quantity,
            List<UUID> modifierIds
    ) {
    }

    record PrepStartedPayload(
            UUID orderId,
            UUID kitchenTicketId,
            UUID actorUserId,
            List<OrderPrepItemSnapshot> items
    ) {
    }

    record TicketRefirePayload(
            UUID orderId,
            UUID originalTicketId,
            UUID refireTicketId,
            UUID actorUserId,
            String reason,
            List<OrderPrepItemSnapshot> items
    ) {
    }

    record ReserveOrderPayload(
            UUID orderId,
            String orderNumber,
            UUID actorUserId,
            List<OrderPrepItemSnapshot> items
    ) {
    }

    String encodeReserveOrder(ReserveOrderPayload payload);

    ReserveOrderPayload decodeReserveOrder(String json);

    String encodePrepStarted(PrepStartedPayload payload);

    PrepStartedPayload decodePrepStarted(String json);

    String encodeTicketRefire(TicketRefirePayload payload);

    TicketRefirePayload decodeTicketRefire(String json);
}
