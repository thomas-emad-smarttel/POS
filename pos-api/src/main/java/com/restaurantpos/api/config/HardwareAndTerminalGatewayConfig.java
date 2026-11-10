package com.restaurantpos.api.config;

import com.restaurantpos.devices.CashDrawer;
import com.restaurantpos.devices.ReceiptPrinter;
import com.restaurantpos.devices.kitchen.EscPosKitchenTicketFormatter;
import com.restaurantpos.devices.payment.CardTerminalGateway;
import com.restaurantpos.devices.payment.VirtualCardTerminalGateway;
import com.restaurantpos.devices.receipt.EscPosReceiptFormatter;
import com.restaurantpos.devices.virtual.VirtualCashDrawer;
import com.restaurantpos.devices.virtual.VirtualReceiptPrinter;
import com.restaurantpos.domain.port.CashDrawerPort;
import com.restaurantpos.domain.port.KitchenPrinterPort;
import com.restaurantpos.domain.port.PaymentAuthorizationPort;
import com.restaurantpos.domain.port.PaymentReversalPort;
import com.restaurantpos.domain.port.PaymentStatusQueryPort;
import com.restaurantpos.domain.port.PaymentTerminalPort;
import com.restaurantpos.domain.port.ReceiptPrinterPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;

/**
 * Hardware and provider-independent payment terminal gateway beans bridging {@code pos-device-integration}
 * into {@code pos-domain} outbound ports. Defaults to virtual adapters in hardware-free environments.
 */
@Configuration
public class HardwareAndTerminalGatewayConfig {

    @Bean
    @ConditionalOnMissingBean(CardTerminalGateway.class)
    public CardTerminalGateway cardTerminalGateway() {
        return new VirtualCardTerminalGateway();
    }

    @Bean
    @ConditionalOnMissingBean(CashDrawer.class)
    public CashDrawer cashDrawer() {
        return new VirtualCashDrawer("Virtual-Cash-Drawer-01");
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "receiptPrinter")
    public ReceiptPrinter receiptPrinter() {
        return new VirtualReceiptPrinter("Virtual-Thermal-Printer-80mm");
    }

    @Bean("kitchenPrinter")
    @ConditionalOnMissingBean(name = "kitchenPrinter")
    public ReceiptPrinter kitchenPrinter() {
        return new VirtualReceiptPrinter("Virtual-Kitchen-Printer-80mm");
    }

    @Bean
    public PaymentTerminalPort paymentTerminalPort(CardTerminalGateway gateway) {
        return new PaymentTerminalPort() {
            @Override
            public TerminalResult startTransaction(StartCommand command) {
                return mapResult(gateway.startTransaction(new CardTerminalGateway.StartPaymentCommand(
                        command.idempotencyKey(),
                        command.currencyCode(),
                        command.amount(),
                        command.terminalRef(),
                        command.simulationHint()
                )));
            }

            @Override
            public TerminalResult cancelTransaction(String idempotencyKey, String providerTxId, String terminalRef) {
                return mapResult(gateway.cancelTransaction(idempotencyKey, providerTxId, terminalRef));
            }
        };
    }

    @Bean
    public PaymentAuthorizationPort paymentAuthorizationPort(PaymentTerminalPort terminalPort) {
        return terminalPort::startTransaction;
    }

    @Bean
    public PaymentStatusQueryPort paymentStatusQueryPort(CardTerminalGateway gateway) {
        return (idempotencyKey, providerTxId, terminalRef) ->
                mapResult(gateway.queryTransactionStatus(idempotencyKey, providerTxId, terminalRef));
    }

    @Bean
    public PaymentReversalPort paymentReversalPort(CardTerminalGateway gateway) {
        return (String providerTxId, BigDecimal amount, String currencyCode, String simulationHint) ->
                mapResult(gateway.reverseTransaction(providerTxId, amount, currencyCode, simulationHint));
    }

    @Bean
    public CashDrawerPort cashDrawerPort(CashDrawer cashDrawer) {
        return () -> {
            String status = cashDrawer.status().name();
            try {
                cashDrawer.openDrawer();
                return new CashDrawerPort.DrawerOpenResult(true, cashDrawer.deviceName(), status, null);
            } catch (Exception ex) {
                return new CashDrawerPort.DrawerOpenResult(false, cashDrawer.deviceName(), status, ex.getMessage());
            }
        };
    }

    @Bean
    public ReceiptPrinterPort receiptPrinterPort(ReceiptPrinter receiptPrinter) {
        return new ReceiptPrinterPort() {
            @Override
            public RenderedAndPrintResult renderAndOptionallyPrint(PrintPayloadRequest req, boolean printToHardware) {
                List<EscPosReceiptFormatter.LineItem> items = req.items() == null ? List.of()
                        : req.items().stream()
                        .map(i -> new EscPosReceiptFormatter.LineItem(i.name(), i.qty(), i.amount(), i.modifiers()))
                        .toList();
                List<EscPosReceiptFormatter.PaymentLine> pays = req.payments() == null ? List.of()
                        : req.payments().stream()
                        .map(p -> new EscPosReceiptFormatter.PaymentLine(p.method(), p.amount(), p.reference()))
                        .toList();

                var rendered = EscPosReceiptFormatter.render(new EscPosReceiptFormatter.ReceiptDocument(
                        req.restaurantName(), req.branchName(), req.branchAddress(), req.branchPhone(),
                        req.receiptNumber(), req.receiptType(), req.language(), req.paperWidth(), req.encoding(),
                        req.duplicateCopy(), req.orderNumber(), req.timestampText(), req.cashierName(),
                        req.currencyCode(), items, req.subtotal(), req.discountTotal(), req.taxTotal(),
                        req.grandTotal(), pays, req.paidTotal(), req.changeAmount(), req.remainingBalance(),
                        req.footerMessage()
                ));
                String base64 = Base64.getEncoder().encodeToString(rendered.escPosBytes());
                if (!printToHardware) {
                    return new RenderedAndPrintResult(rendered.plainText(), base64, "GENERATED", null);
                }
                return sendBytesToHardware(receiptPrinter, rendered.plainText(), rendered.escPosBytes(), base64);
            }

            @Override
            public RenderedAndPrintResult reprintRawEscPos(String escPosBase64) {
                byte[] bytes = Base64.getDecoder().decode(escPosBase64);
                return sendBytesToHardware(receiptPrinter, "", bytes, escPosBase64);
            }

            private RenderedAndPrintResult sendBytesToHardware(
                    ReceiptPrinter printer,
                    String plainText,
                    byte[] bytes,
                    String base64
            ) {
                try {
                    printer.printReceipt(bytes);
                    printer.cutPaper();
                    return new RenderedAndPrintResult(plainText, base64, "PRINTED", null);
                } catch (Exception ex) {
                    String status = printer.status().name().equals("DISCONNECTED")
                            ? "PRINTER_UNAVAILABLE" : "FAILED";
                    return new RenderedAndPrintResult(plainText, base64, status, ex.getMessage());
                }
            }
        };
    }

    private static PaymentTerminalPort.TerminalResult mapResult(CardTerminalGateway.TerminalTransactionResult r) {
        PaymentTerminalPort.Outcome outcome = switch (r.outcome()) {
            case APPROVED -> PaymentTerminalPort.Outcome.APPROVED;
            case DECLINED -> PaymentTerminalPort.Outcome.DECLINED;
            case UNCERTAIN_TIMEOUT -> PaymentTerminalPort.Outcome.UNCERTAIN_TIMEOUT;
            case CANCELLED -> PaymentTerminalPort.Outcome.CANCELLED;
            case REVERSED -> PaymentTerminalPort.Outcome.REVERSED;
        };
        return new PaymentTerminalPort.TerminalResult(
                outcome, r.providerName(), r.providerTxId(), r.terminalRef(),
                r.maskedPan(), r.cardBrand(), r.authCode(), r.responseCode(), r.message()
        );
    }

    @Bean
    public KitchenPrinterPort kitchenPrinterPort(@Qualifier("kitchenPrinter") ReceiptPrinter kitchenPrinter) {
        return new KitchenPrinterPort() {
            @Override
            public PrintOutcome renderAndPrint(PrintTicketRequest req) {
                List<EscPosKitchenTicketFormatter.TicketLine> lines = req.lines() == null ? List.of()
                        : req.lines().stream()
                        .map(l -> new EscPosKitchenTicketFormatter.TicketLine(
                                l.itemName(), l.variantName(), l.quantity(),
                                l.modifiers(), l.prepInstructions(), l.notes()))
                        .toList();

                var rendered = EscPosKitchenTicketFormatter.render(new EscPosKitchenTicketFormatter.KitchenTicketDocument(
                        req.stationName(), req.ticketNumber(), req.ticketKind(), req.jobType(),
                        req.orderNumber(), req.orderType(), req.tableLabel(), req.waiterName(),
                        req.priorityLevel(), req.timestampText(), req.language(), req.paperWidth(),
                        req.encoding(), req.noticeReason(), lines
                ));
                String base64 = Base64.getEncoder().encodeToString(rendered.escPosBytes());
                return sendBytes(kitchenPrinter, rendered.plainText(), rendered.escPosBytes(), base64);
            }

            @Override
            public PrintOutcome retryRawPrint(String escPosBase64) {
                byte[] bytes = Base64.getDecoder().decode(escPosBase64);
                return sendBytes(kitchenPrinter, "", bytes, escPosBase64);
            }

            private PrintOutcome sendBytes(ReceiptPrinter printer, String text, byte[] bytes, String base64) {
                try {
                    printer.printReceipt(bytes);
                    printer.cutPaper();
                    return new PrintOutcome(printer.deviceName(), text, base64, "PRINTED", null);
                } catch (Exception ex) {
                    String status = "DISCONNECTED".equals(printer.status().name())
                            ? "PRINTER_UNAVAILABLE" : "FAILED";
                    return new PrintOutcome(printer.deviceName(), text, base64, status, ex.getMessage());
                }
            }
        };
    }
}
