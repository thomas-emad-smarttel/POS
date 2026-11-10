package com.restaurantpos.domain.port;

import java.util.List;

public interface ReceiptPrinterPort {

    record LineItem(String name, String qty, String amount, List<String> modifiers) {
    }

    record PaymentLine(String method, String amount, String reference) {
    }

    record PrintPayloadRequest(
            String restaurantName,
            String branchName,
            String branchAddress,
            String branchPhone,
            String receiptNumber,
            String receiptType,
            String language,
            int paperWidth,
            String encoding,
            boolean duplicateCopy,
            String orderNumber,
            String timestampText,
            String cashierName,
            String currencyCode,
            List<LineItem> items,
            String subtotal,
            String discountTotal,
            String taxTotal,
            String grandTotal,
            List<PaymentLine> payments,
            String paidTotal,
            String changeAmount,
            String remainingBalance,
            String footerMessage
    ) {
    }

    record RenderedAndPrintResult(
            String renderedText,
            String escPosBase64,
            String printStatus,
            String errorMessage
    ) {
    }

    RenderedAndPrintResult renderAndOptionallyPrint(PrintPayloadRequest request, boolean printToHardware);

    RenderedAndPrintResult reprintRawEscPos(String escPosBase64);
}
