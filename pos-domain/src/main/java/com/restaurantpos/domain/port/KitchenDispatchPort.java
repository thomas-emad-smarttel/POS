package com.restaurantpos.domain.port;

import com.restaurantpos.domain.order.OrderAggregate;

/**
 * Outbound port notifying the kitchen outbox when order items are dispatched or cancelled after kitchen fire.
 */
public interface KitchenDispatchPort {
    void dispatchOrderToKitchen(OrderAggregate order);

    default void dispatchOrderCancelled(OrderAggregate order, String reason) {
    }
}
