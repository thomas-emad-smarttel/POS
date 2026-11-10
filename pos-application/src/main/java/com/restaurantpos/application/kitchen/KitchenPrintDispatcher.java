package com.restaurantpos.application.kitchen;

import com.restaurantpos.application.audit.AuditRecordingService;
import com.restaurantpos.domain.kitchen.KitchenPrintJob;
import com.restaurantpos.domain.kitchen.KitchenStation;
import com.restaurantpos.domain.kitchen.KitchenTicket;
import com.restaurantpos.domain.port.KitchenPrinterPort;
import com.restaurantpos.domain.port.KitchenRepositoryPort;
import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.security.AuditAction;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared idempotent kitchen ticket ESC/POS printer dispatcher.
 * Printer unavailability or IO errors never throw or roll back committed kitchen tickets.
 */
public class KitchenPrintDispatcher {

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);

    private final KitchenRepositoryPort kitchenRepo;
    private final KitchenPrinterPort printerPort;
    private final AuditRecordingService auditService;
    private final SystemClockPort clock;

    public KitchenPrintDispatcher(
            KitchenRepositoryPort kitchenRepo,
            KitchenPrinterPort printerPort,
            AuditRecordingService auditService,
            SystemClockPort clock
    ) {
        this.kitchenRepo = kitchenRepo;
        this.printerPort = printerPort;
        this.auditService = auditService;
        this.clock = clock;
    }

    public KitchenPrintJob dispatchTicketPrint(
            KitchenTicket ticket,
            KitchenStation station,
            String jobType,
            String noticeReason,
            UUID actorUserId,
            CorrelationId corr
    ) {
        String idempKey = "kprint-" + ticket.id() + "-" + jobType;
        Optional<KitchenPrintJob> existing = kitchenRepo.findPrintJobByIdempotency(
                ticket.tenantId(), ticket.branchId(), idempKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        List<KitchenPrinterPort.TicketLine> lines = ticket.items().stream()
                .map(i -> new KitchenPrinterPort.TicketLine(
                        i.itemNameSnapshot(),
                        i.variantNameSnapshot(),
                        i.quantity().stripTrailingZeros().toPlainString(),
                        i.selectedModifiers(),
                        i.prepInstructions(),
                        i.notes()
                ))
                .toList();

        Instant now = clock.now();
        String lang = station.receiptLanguage() != null ? station.receiptLanguage() : "BILINGUAL";
        int width = station.paperWidth() >= 32 ? station.paperWidth() : 42;
        String enc = station.receiptEncoding() != null ? station.receiptEncoding() : "UTF-8";

        KitchenPrinterPort.PrintOutcome outcome = printerPort.renderAndPrint(new KitchenPrinterPort.PrintTicketRequest(
                station.name(),
                ticket.ticketNumber(),
                ticket.ticketKind(),
                jobType,
                ticket.orderNumberSnapshot(),
                ticket.orderTypeSnapshot(),
                ticket.tableLabelSnapshot(),
                ticket.waiterNameSnapshot(),
                ticket.priorityLevel().name(),
                TS_FMT.format(now),
                lang,
                width,
                enc,
                noticeReason,
                lines
        ));

        KitchenPrintJob job = kitchenRepo.savePrintJob(new KitchenPrintJob(
                UUID.randomUUID(),
                ticket.tenantId(),
                ticket.branchId(),
                ticket.id(),
                station.id(),
                jobType,
                outcome.printerName(),
                lang,
                width,
                enc,
                outcome.renderedText(),
                outcome.escPosBase64(),
                outcome.printStatus(),
                1,
                outcome.errorMessage(),
                idempKey,
                now,
                now
        ));

        if (!"PRINTED".equals(job.status())) {
            auditService.record(ticket.tenantId(), ticket.branchId(), actorUserId, AuditAction.KITCHEN_PRINTER_FAILED,
                    "KitchenPrintJob", job.id().toString(),
                    "{\"status\":\"" + job.status() + "\",\"station\":\"" + station.code() + "\"}", corr);
        }
        return job;
    }

    public KitchenPrintJob retryJob(KitchenPrintJob existing, UUID actorUserId, CorrelationId corr) {
        KitchenPrinterPort.PrintOutcome outcome = printerPort.retryRawPrint(existing.escPosBase64());
        Instant now = clock.now();
        KitchenPrintJob updated = kitchenRepo.savePrintJob(new KitchenPrintJob(
                existing.id(),
                existing.tenantId(),
                existing.branchId(),
                existing.kitchenTicketId(),
                existing.kitchenStationId(),
                existing.jobType(),
                outcome.printerName(),
                existing.language(),
                existing.paperWidth(),
                existing.encoding(),
                existing.renderedText(),
                existing.escPosBase64(),
                outcome.printStatus(),
                existing.attemptCount() + 1,
                outcome.errorMessage(),
                existing.idempotencyKey(),
                existing.createdAt(),
                now
        ));
        if (!"PRINTED".equals(updated.status())) {
            auditService.record(updated.tenantId(), updated.branchId(), actorUserId, AuditAction.KITCHEN_PRINTER_FAILED,
                    "KitchenPrintJob", updated.id().toString(),
                    "{\"attempt\":" + updated.attemptCount() + ",\"status\":\"" + updated.status() + "\"}", corr);
        }
        return updated;
    }
}
