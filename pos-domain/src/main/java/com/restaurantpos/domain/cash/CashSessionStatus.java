package com.restaurantpos.domain.cash;

public enum CashSessionStatus {
    OPEN,
    CLOSED,
    RECONCILED,
    REOPENED;

    public boolean isActive() {
        return this == OPEN || this == REOPENED;
    }
}
