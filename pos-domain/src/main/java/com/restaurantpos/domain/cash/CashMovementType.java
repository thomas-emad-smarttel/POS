package com.restaurantpos.domain.cash;

public enum CashMovementType {
    OPENING_FLOAT(1),
    CASH_SALE(1),
    PAY_IN(1),
    DEPOSIT(1),
    CASH_REFUND(-1),
    PAY_OUT(-1),
    WITHDRAWAL(-1),
    SAFE_DROP(-1),
    DRAWER_NO_SALE(0);

    private final int sign;

    CashMovementType(int sign) {
        this.sign = sign;
    }

    public int sign() {
        return sign;
    }
}
