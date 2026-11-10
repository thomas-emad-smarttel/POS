package com.restaurantpos.domain.port;

import java.util.List;

public interface KitchenPrinterPort {

    record TicketLine(
            String itemName,
            String variantName,
            String quantity,
            List<String> modifiers,
            String prepInstructions,
            String notes
    ) {
    }

    record PrintTicketRequest(
            String stationName,
            String ticketNumber,
            String ticketKind,
            String jobType,
            String orderNumber,
            String orderType,
            String tableLabel,
            String waiterName,
            String priorityLevel,
            String timestampText,
            String language,
            int paperWidth,
            String encoding,
            String noticeReason,
            List<TicketLine> lines
    ) {
    }

    record PrintOutcome(
            String printerName,
            String renderedText,
            String escPosBase64,
            String printStatus,
            String errorMessage
    ) {
    }

    PrintOutcome renderAndPrint(PrintTicketRequest request);

    PrintOutcome retryRawPrint(String escPosBase64);
}
