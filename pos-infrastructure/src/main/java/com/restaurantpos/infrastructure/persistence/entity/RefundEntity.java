package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "refunds")
public class RefundEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "original_payment_id", nullable = false, updatable = false)
    private UUID originalPaymentId;

    @Column(name = "issued_by_user_id", nullable = false, updatable = false)
    private UUID issuedByUserId;

    @Column(name = "approved_by_user_id", updatable = false)
    private UUID approvedByUserId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", nullable = false, updatable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currencyCode;

    @Column(name = "amount", nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal amount;

    @Column(name = "status", nullable = false, updatable = false, length = 32)
    private String status;

    @Column(name = "reason", nullable = false, updatable = false, length = 300)
    private String reason;

    @Column(name = "failure_reason", updatable = false, length = 300)
    private String failureReason;

    @Column(name = "external_reference", updatable = false, length = 128)
    private String externalReference;

    @Column(name = "refunded_at", nullable = false, updatable = false)
    private Instant refundedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefundEntity() {
    }

    public RefundEntity(
            UUID id, UUID tenantId, UUID branchId, UUID orderId, UUID originalPaymentId,
            UUID issuedByUserId, UUID approvedByUserId, String idempotencyKey, String currencyCode,
            BigDecimal amount, String status, String reason, String failureReason,
            String externalReference, Instant refundedAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.orderId = orderId;
        this.originalPaymentId = originalPaymentId;
        this.issuedByUserId = issuedByUserId;
        this.approvedByUserId = approvedByUserId;
        this.idempotencyKey = idempotencyKey;
        this.currencyCode = currencyCode;
        this.amount = amount;
        this.status = status;
        this.reason = reason;
        this.failureReason = failureReason;
        this.externalReference = externalReference;
        this.refundedAt = refundedAt;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public UUID getOrderId() { return orderId; }
    public UUID getOriginalPaymentId() { return originalPaymentId; }
    public UUID getIssuedByUserId() { return issuedByUserId; }
    public UUID getApprovedByUserId() { return approvedByUserId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public String getFailureReason() { return failureReason; }
    public String getExternalReference() { return externalReference; }
    public Instant getRefundedAt() { return refundedAt; }
}
