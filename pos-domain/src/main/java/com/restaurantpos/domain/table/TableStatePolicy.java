package com.restaurantpos.domain.table;

import com.restaurantpos.domain.exception.DomainException;

/**
 * Enforces valid restaurant dining table state transitions and concurrency safeguards.
 */
public final class TableStatePolicy {

    private TableStatePolicy() {
    }

    public static void requireAvailableForOrder(DiningTable table) {
        if (!table.active()) {
            throw new DomainException("TABLE_INACTIVE", "Table " + table.tableCode() + " is inactive");
        }
        if (table.activeOrderId() != null || table.status() == TableStatus.OCCUPIED) {
            throw new DomainException("TABLE_OCCUPIED", "Table " + table.tableCode() + " is already occupied");
        }
        if (table.status() == TableStatus.CLEANING || table.status() == TableStatus.OUT_OF_SERVICE) {
            throw new DomainException("TABLE_UNAVAILABLE", "Table " + table.tableCode() + " is currently " + table.status());
        }
    }

    public static void validateManualTransition(DiningTable table, TableStatus target) {
        if (table.activeOrderId() != null && target != TableStatus.OCCUPIED) {
            throw new DomainException(
                    "TABLE_HAS_ACTIVE_ORDER",
                    "Cannot change table " + table.tableCode() + " to " + target + " while an active order is attached"
            );
        }
        if (target == TableStatus.OCCUPIED && table.activeOrderId() == null) {
            throw new DomainException(
                    "TABLE_REQUIRES_ORDER",
                    "Table status OCCUPIED requires an active order assignment"
            );
        }
    }
}
