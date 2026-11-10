package com.restaurantpos.devices;

/**
 * Hardware abstraction interface for ESC/POS or vendor thermal receipt/kitchen printers.
 */
public interface ReceiptPrinter {
    String deviceName();

    DeviceStatus status();

    void printReceipt(byte[] escPosPayload);

    void cutPaper();
}
