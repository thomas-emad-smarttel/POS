package com.restaurantpos.domain.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Codec converting outbox kitchen dispatch payloads without leaking Jackson dependencies into pos-domain/pos-application.
 */
public interface KitchenPayloadCodecPort {

    record DispatchedItemSnapshot(
            UUID orderItemId,
            UUID productId,
            UUID categoryId,
            String productName,
            String variantName,
            BigDecimal quantity,
            List<String> modifiers,
            String prepInstructions,
            String kitchenNotes
    ) {
    }

    record DispatchedOrderSnapshot(
            UUID orderId,
            String orderNumber,
            String orderType,
            String tableLabel,
            UUID waiterId,
            String waiterName,
            List<DispatchedItemSnapshot> items
    ) {
    }

    record CancelledOrderSnapshot(
            UUID orderId,
            String orderNumber,
            String reason
    ) {
    }

    String encodeOrderDispatched(DispatchedOrderSnapshot snapshot);

    DispatchedOrderSnapshot decodeOrderDispatched(String payloadJson);

    String encodeOrderCancelled(CancelledOrderSnapshot snapshot);

    CancelledOrderSnapshot decodeOrderCancelled(String payloadJson);
}
