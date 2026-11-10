package com.restaurantpos.devices;

import java.util.function.Consumer;

/**
 * Hardware abstraction interface for HID/serial barcode scanners.
 */
public interface BarcodeScanner {
    String deviceName();

    DeviceStatus status();

    void onScan(Consumer<String> barcodeListener);
}
