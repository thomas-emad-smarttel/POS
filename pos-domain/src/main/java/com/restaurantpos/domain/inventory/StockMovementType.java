package com.restaurantpos.domain.inventory;

public enum StockMovementType {
    OPENING_BALANCE("IN"),
    OPENING_STOCK("IN"),
    PURCHASE_RECEIPT("IN"),
    SALE_CONSUMPTION("OUT"),
    SALE_DEDUCTION("OUT"),
    RESERVATION("RESERVE"),
    RESERVATION_RELEASE("RELEASE"),
    WASTE("OUT"),
    DAMAGE("OUT"),
    STOCK_ADJUSTMENT_IN("IN"),
    STOCK_ADJUSTMENT_OUT("OUT"),
    ADJUSTMENT("IN"),
    TRANSFER_OUT("OUT"),
    TRANSFER_IN("IN"),
    CUSTOMER_RETURN("IN"),
    SUPPLIER_RETURN("OUT"),
    PRODUCTION_CONSUMPTION("OUT"),
    PRODUCTION_OUTPUT("IN"),
    REVERSAL("IN");

    private final String defaultDirection;

    StockMovementType(String defaultDirection) {
        this.defaultDirection = defaultDirection;
    }

    public String defaultDirection() {
        return defaultDirection;
    }
}
