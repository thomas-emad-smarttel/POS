package com.restaurantpos.domain.kitchen;

public enum KitchenTicketStatus {
    NEW,
    QUEUED,
    ACCEPTED,
    IN_PROGRESS,
    PREPARING,
    READY,
    SERVED,
    BUMPED,
    CANCELLED,
    ON_HOLD;

    public boolean isTerminal() {
        return this == SERVED || this == BUMPED || this == CANCELLED;
    }
}
