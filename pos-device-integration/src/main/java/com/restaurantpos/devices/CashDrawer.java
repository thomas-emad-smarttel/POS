package com.restaurantpos.devices;

/**
 * Hardware abstraction interface for cash drawers (typically DK-kick via printer or USB/serial).
 */
public interface CashDrawer {
    String deviceName();

    DeviceStatus status();

    void openDrawer();
}
