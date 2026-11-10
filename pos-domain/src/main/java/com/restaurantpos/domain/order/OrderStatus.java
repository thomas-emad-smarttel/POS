package com.restaurantpos.domain.order;

/**
 * Lifecycle states of a branch order.
 */
public enum OrderStatus {
    DRAFT,
    OPEN,
    ON_HOLD,
    IN_KITCHEN,
    READY,
    SERVED,
    PAID,
    PARTIALLY_REFUNDED,
    CANCELLED,
    REFUNDED
}
