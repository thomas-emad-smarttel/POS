package com.restaurantpos.domain.kitchen;

public enum KitchenTicketItemStatus {
    NEW,
    QUEUED,
    PREPARING,
    IN_PROGRESS,
    READY,
    SERVED,
    CANCELLED,
    VOIDED;

    public boolean isCancelledOrVoided() {
        return this == CANCELLED || this == VOIDED;
    }

    public boolean satisfiesTicketReadiness() {
        return this == READY || this == SERVED || this == CANCELLED || this == VOIDED;
    }
}
