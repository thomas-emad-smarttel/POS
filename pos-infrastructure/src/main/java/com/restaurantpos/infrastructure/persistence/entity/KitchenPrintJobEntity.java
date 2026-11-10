package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kitchen_print_jobs")
public class KitchenPrintJobEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "kitchen_ticket_id", nullable = false, updatable = false)
    private UUID kitchenTicketId;

    @Column(name = "kitchen_station_id", nullable = false, updatable = false)
    private UUID kitchenStationId;

    @Column(name = "job_type", nullable = false, updatable = false, length = 32)
    private String jobType;

    @Column(name = "printer_name", nullable = false, length = 120)
    private String printerName;

    @Column(name = "language", nullable = false, length = 16)
    private String language;

    @Column(name = "paper_width", nullable = false)
    private int paperWidth;

    @Column(name = "encoding", nullable = false, length = 32)
    private String encoding;

    @Column(name = "rendered_text", nullable = false, columnDefinition = "TEXT")
    private String renderedText;

    @Column(name = "escpos_base64", nullable = false, columnDefinition = "TEXT")
    private String escPosBase64;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "error_message", length = 300)
    private String errorMessage;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_attempt_at", nullable = false)
    private Instant lastAttemptAt;

    protected KitchenPrintJobEntity() {
    }

    public KitchenPrintJobEntity(
            UUID id, UUID tenantId, UUID branchId, UUID kitchenTicketId, UUID kitchenStationId,
            String jobType, String printerName, String language, int paperWidth, String encoding,
            String renderedText, String escPosBase64, String status, int attemptCount,
            String errorMessage, String idempotencyKey, Instant createdAt, Instant lastAttemptAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.kitchenTicketId = kitchenTicketId;
        this.kitchenStationId = kitchenStationId;
        this.jobType = jobType;
        this.printerName = printerName;
        this.language = language;
        this.paperWidth = paperWidth;
        this.encoding = encoding;
        this.renderedText = renderedText;
        this.escPosBase64 = escPosBase64;
        this.status = status;
        this.attemptCount = attemptCount;
        this.errorMessage = errorMessage;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.lastAttemptAt = lastAttemptAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public UUID getKitchenTicketId() { return kitchenTicketId; }
    public UUID getKitchenStationId() { return kitchenStationId; }
    public String getJobType() { return jobType; }
    public String getPrinterName() { return printerName; }
    public void setPrinterName(String printerName) { this.printerName = printerName; }
    public String getLanguage() { return language; }
    public int getPaperWidth() { return paperWidth; }
    public String getEncoding() { return encoding; }
    public String getRenderedText() { return renderedText; }
    public String getEscPosBase64() { return escPosBase64; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(Instant lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }
}
