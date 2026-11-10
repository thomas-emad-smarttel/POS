package com.restaurantpos.devices;

import com.restaurantpos.devices.kitchen.EscPosKitchenTicketFormatter;
import com.restaurantpos.devices.kitchen.EscPosKitchenTicketFormatter.KitchenTicketDocument;
import com.restaurantpos.devices.kitchen.EscPosKitchenTicketFormatter.TicketLine;
import com.restaurantpos.devices.virtual.VirtualReceiptPrinter;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitchenPrinterAdapterTest {

    @Test
    void formatsKitchenTicketsAcrossJobTypesAndLanguages() {
        KitchenTicketDocument refireDoc = new KitchenTicketDocument(
                "Hot Grill",
                "KT-104",
                "REFIRE",
                "REFIRE",
                "ORD-55",
                "DINE_IN",
                "Table 4",
                "Omar",
                "RUSH",
                "12:15:00",
                "BILINGUAL",
                42,
                "UTF-8",
                "Steak undercooked - remake",
                List.of(new TicketLine(
                        "Ribeye Steak",
                        "300g",
                        "1",
                        List.of("Medium Rare", "Peppercorn Sauce"),
                        "Sear extra hot",
                        "Allergy: no sesame"
                ))
        );

        var rendered = EscPosKitchenTicketFormatter.render(refireDoc);
        assertTrue(rendered.plainText().contains("REFIRE"));
        assertTrue(rendered.plainText().contains("[ HOT GRILL ]"));
        assertTrue(rendered.plainText().contains("ORD-55"));
        assertTrue(rendered.plainText().contains("Ribeye Steak [300g]"));
        assertTrue(rendered.plainText().contains("+ Medium Rare"));
        assertTrue(rendered.plainText().contains("[PREP] Sear extra hot"));
        assertTrue(rendered.plainText().contains("[NOTE] Allergy: no sesame"));
        assertTrue(rendered.plainText().contains("Steak undercooked - remake"));
        // Starts with ESC @ (0x1B 0x40) and ends with GS V cut (0x1D 0x56 0x42 0x00)
        assertEquals(0x1B, rendered.escPosBytes()[0]);
        assertEquals(0x40, rendered.escPosBytes()[1]);

        KitchenTicketDocument supplementalAr = new KitchenTicketDocument(
                "Bar",
                "KT-105",
                "SUPPLEMENTAL",
                "SUPPLEMENTAL",
                "ORD-55",
                "DINE_IN",
                "Table 4",
                "Omar",
                "NORMAL",
                "12:18:00",
                "AR",
                48,
                "UTF-8",
                null,
                List.of(new TicketLine("Mint Lemonade", null, "2", List.of(), null, null))
        );
        var arRendered = EscPosKitchenTicketFormatter.render(supplementalAr);
        String bytesText = new String(arRendered.escPosBytes(), StandardCharsets.UTF_8);
        assertTrue(bytesText.contains("إضافة"));
        assertTrue(bytesText.contains("Mint Lemonade"));
    }

    @Test
    void virtualPrinterSupportsUnavailableToggleAndCleanRecoveryForRetries() {
        VirtualReceiptPrinter printer = new VirtualReceiptPrinter("Virtual-Kitchen-Test");
        assertEquals(DeviceStatus.SIMULATED, printer.status());

        printer.setUnavailable(true);
        assertEquals(DeviceStatus.DISCONNECTED, printer.status());
        assertThrows(IllegalStateException.class, () -> printer.printReceipt("test".getBytes(StandardCharsets.UTF_8)));
        assertEquals(0, printer.capturedPayloads().size());

        printer.setUnavailable(false);
        assertEquals(DeviceStatus.SIMULATED, printer.status());
        printer.printReceipt("recovered".getBytes(StandardCharsets.UTF_8));
        printer.cutPaper();
        assertEquals(1, printer.capturedPayloads().size());
        assertEquals(1, printer.cutCount());
    }
}
