package com.restaurantpos.devices;

import com.restaurantpos.devices.virtual.VirtualCashDrawer;
import com.restaurantpos.devices.virtual.VirtualReceiptPrinter;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VirtualHardwareAdaptersTest {

    @Test
    void virtualPrinterAndDrawerRecordOperationsWithoutPhysicalHardware() {
        VirtualReceiptPrinter printer = new VirtualReceiptPrinter("FrontCounter-80mm");
        VirtualCashDrawer drawer = new VirtualCashDrawer("Drawer-1");

        printer.printReceipt("ORDER #1001".getBytes(StandardCharsets.UTF_8));
        printer.cutPaper();
        drawer.openDrawer();

        assertEquals(DeviceStatus.SIMULATED, printer.status());
        assertEquals(1, printer.capturedPayloads().size());
        assertEquals(1, printer.cutCount());
        assertEquals(1, drawer.openCount());
    }
}
