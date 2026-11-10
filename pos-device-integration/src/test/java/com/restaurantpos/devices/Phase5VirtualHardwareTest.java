package com.restaurantpos.devices;

import com.restaurantpos.devices.payment.CardTerminalGateway;
import com.restaurantpos.devices.payment.VirtualCardTerminalGateway;
import com.restaurantpos.devices.receipt.EscPosReceiptFormatter;
import com.restaurantpos.devices.virtual.VirtualCashDrawer;
import com.restaurantpos.devices.virtual.VirtualReceiptPrinter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase5VirtualHardwareTest {

    @Test
    void virtualCardTerminalSupportsApprovalsDeclinesTimeoutsReversalsAndSafeMetadata() {
        VirtualCardTerminalGateway gateway = new VirtualCardTerminalGateway();

        var approved = gateway.startTransaction(new CardTerminalGateway.StartPaymentCommand(
                "k-1", "USD", new BigDecimal("25.00"), "TERM-01", "APPROVED"));
        assertEquals(CardTerminalGateway.TerminalOutcome.APPROVED, approved.outcome());
        assertEquals("**** **** **** 4242", approved.maskedPan());
        assertEquals("VISA", approved.cardBrand());

        var declined = gateway.startTransaction(new CardTerminalGateway.StartPaymentCommand(
                "k-2", "USD", new BigDecimal("25.00"), "TERM-01", "DECLINED"));
        assertEquals(CardTerminalGateway.TerminalOutcome.DECLINED, declined.outcome());
        assertNull(declined.authCode());

        var timedOut = gateway.startTransaction(new CardTerminalGateway.StartPaymentCommand(
                "k-3", "USD", new BigDecimal("25.00"), "TERM-01", "TIMEOUT_THEN_APPROVED"));
        assertEquals(CardTerminalGateway.TerminalOutcome.UNCERTAIN_TIMEOUT, timedOut.outcome());

        var queried = gateway.queryTransactionStatus("k-3", timedOut.providerTxId(), "TERM-01");
        assertEquals(CardTerminalGateway.TerminalOutcome.APPROVED, queried.outcome());

        var reversed = gateway.reverseTransaction(approved.providerTxId(), new BigDecimal("25.00"), "USD", null);
        assertEquals(CardTerminalGateway.TerminalOutcome.REVERSED, reversed.outcome());
    }

    @Test
    void formatsBilingualEscPosReceiptsAndHandlesUnavailableHardware() {
        var rendered = EscPosReceiptFormatter.render(new EscPosReceiptFormatter.ReceiptDocument(
                "Al-Noor Kitchen", "Downtown", "Main St 1", "555-0100",
                "RCP-1001", "DUPLICATE", "BILINGUAL", 42, "UTF-8", true,
                "ORD-50", "2026-11-10 15:00:00", "Cashier Ali", "USD",
                List.of(new EscPosReceiptFormatter.LineItem("Burger", "1", "10.00", List.of("Extra Cheese"))),
                "10.00", "0.00", "1.50", "11.50",
                List.of(new EscPosReceiptFormatter.PaymentLine("Cash", "11.50", null)),
                "11.50", "0.00", "0.00", "Thank you"
        ));
        assertTrue(rendered.plainText().contains("DUPLICATE"));
        assertTrue(rendered.plainText().contains("المجموع الفرعي"));
        assertTrue(rendered.escPosBytes().length > 20);
        assertEquals(0x1B, rendered.escPosBytes()[0]);

        VirtualReceiptPrinter printer = new VirtualReceiptPrinter("TestPrinter");
        printer.setUnavailable(true);
        assertEquals(DeviceStatus.DISCONNECTED, printer.status());
        assertThrows(IllegalStateException.class, () -> printer.printReceipt(rendered.escPosBytes()));

        VirtualCashDrawer drawer = new VirtualCashDrawer("TestDrawer");
        drawer.setFailureMessage("DK Solenoid jam");
        assertEquals(DeviceStatus.ERROR, drawer.status());
        assertThrows(IllegalStateException.class, drawer::openDrawer);
    }
}
