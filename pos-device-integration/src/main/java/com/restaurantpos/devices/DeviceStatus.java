package com.restaurantpos.devices;

/**
 * Hardware connection health state reported by device adapters.
 */
public enum DeviceStatus {
    CONNECTED,
    DISCONNECTED,
    PAPER_LOW,
    ERROR,
    SIMULATED
}
