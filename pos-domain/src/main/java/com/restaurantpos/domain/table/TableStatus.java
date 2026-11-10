package com.restaurantpos.domain.table;

/**
 * Valid operational states for a restaurant dining table.
 */
public enum TableStatus {
    AVAILABLE,
    OCCUPIED,
    RESERVED,
    CLEANING,
    OUT_OF_SERVICE
}
