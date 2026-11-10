package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "receipts")
public class ReceiptEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "order_id", updatable = false)
    private UUID orderId;

    @Column(name = "payment_id", updatable = false)
    private UUID paymentId;

    @Column(name = "refund_id", updatable = false)
    private UUID refundId;

    @Column(name = "cash_session_id", updatable = false)
    private UUID cashSessionId;

    @Column(name = "receipt_number", nullable = false, updatable = false, length = 64)
    private String receiptNumber;

    @Column(name = "receipt_type", nullable = false, updatable = false, length = 32)
    private String receiptType;

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

    @Column(name = "print_count", nullable = false)
    private int printCount;

    @Column(name = "last_print_status", nullable = false, length = 32)
    private String lastPrintStatus;

    @Column(name = "last_print_error", length = 300)
    private String lastPrintError;

    @Column(name = "last_printed_at")
    private Instant lastPrintedAt;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReceiptEntity() {
    }

    public ReceiptEntity(
            UUID id, UUID tenantId, UUID branchId, UUID orderId, UUID paymentId,
            UUID refundId, UUID cashSessionId, String receiptNumber, String receiptType,
            String language, int paperWidth, String encoding, String renderedText,
            String escPosBase64, int printCount, String lastPrintStatus, String lastPrintError,
            Instant lastPrintedAt, UUID createdByUserId, Instant createdAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.refundId = refundId;
        this.cashSessionId = cashSessionId;
        this.receiptNumber = receiptNumber;
        this.receiptType = receiptType;
        this.language = language;
        this.paperWidth = paperWidth;
        this.encoding = encoding;
        this.renderedText = renderedText;
        this.escPosBase64 = escPosBase64;
        this.printCount = printCount;
        this.lastPrintStatus = lastPrintStatus;
        this.lastPrintError = lastPrintError;
        this.lastPrintedAt = lastPrintedAt;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public UUID getOrderId() { return orderId; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getRefundId() { return refundId; }
    public UUID getCashSessionId() { return cashSessionId; }
    public String getReceiptNumber() { return receiptNumber; }
    public String getReceiptType() { return receiptType; }
    public String getLanguage() { return language; }
    public int getPaperWidth() { return paperWidth; }
    public String getEncoding() { return encoding; }
    public String getRenderedText() { return renderedText; }
    public void setRenderedText(String renderedText) { this.renderedText = renderedText; }
    public String getEscPosBase64() { return escPosBase64; }
    public int getPrintCount() { return printCount; }
    public void setPrintCount(int printCount) { this.printCount = printCount; }
    public String getLastPrintStatus() { return lastPrintStatus; }
    public void setLastPrintStatus(String lastPrintStatus) { this.lastPrintStatus = lastPrintStatus; }
    public String getLastPrintError() { return lastPrintError; }
    public void setLastPrintError(String lastPrintError) { this.lastPrintError = lastPrintError; }
    public Instant getLastPrintedAt() { return lastPrintedAt; }
    public void setLastPrintedAt(Instant lastPrintedAt) { this.lastPrintedAt = lastPrintedAt; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
