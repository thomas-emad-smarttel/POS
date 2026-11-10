package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttemptEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "payment_method_id", nullable = false, updatable = false)
    private UUID paymentMethodId;

    @Column(name = "cash_session_id", updatable = false)
    private UUID cashSessionId;

    @Column(name = "initiated_by_user_id", nullable = false, updatable = false)
    private UUID initiatedByUserId;

    @Column(name = "linked_payment_id")
    private UUID linkedPaymentId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", nullable = false, updatable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currencyCode;

    @Column(name = "requested_amount", nullable = false, precision = 14, scale = 4)
    private BigDecimal requestedAmount;

    @Column(name = "tendered_amount", nullable = false, precision = 14, scale = 4)
    private BigDecimal tenderedAmount;

    @Column(name = "change_amount", nullable = false, precision = 14, scale = 4)
    private BigDecimal changeAmount;

    @Column(name = "tip_amount", nullable = false, precision = 14, scale = 4)
    private BigDecimal tipAmount;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "provider_name", nullable = false, length = 64)
    private String providerName;

    @Column(name = "provider_tx_id", length = 128)
    private String providerTxId;

    @Column(name = "terminal_ref", length = 64)
    private String terminalRef;

    @Column(name = "masked_pan", length = 32)
    private String maskedPan;

    @Column(name = "card_brand", length = 32)
    private String cardBrand;

    @Column(name = "auth_code", length = 64)
    private String authCode;

    @Column(name = "response_code", length = 32)
    private String responseCode;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @Column(name = "reconciled", nullable = false)
    private boolean reconciled;

    @Column(name = "reconciled_at")
    private Instant reconciledAt;

    @Column(name = "reconciled_by_user_id")
    private UUID reconciledByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PaymentAttemptEntity() {
    }

    public PaymentAttemptEntity(
            UUID id, UUID tenantId, UUID branchId, UUID orderId, UUID paymentMethodId,
            UUID cashSessionId, UUID initiatedByUserId, String idempotencyKey,
            String currencyCode, BigDecimal requestedAmount, BigDecimal tenderedAmount,
            BigDecimal changeAmount, BigDecimal tipAmount, String status, String providerName,
            Instant createdAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.orderId = orderId;
        this.paymentMethodId = paymentMethodId;
        this.cashSessionId = cashSessionId;
        this.initiatedByUserId = initiatedByUserId;
        this.idempotencyKey = idempotencyKey;
        this.currencyCode = currencyCode;
        this.requestedAmount = requestedAmount;
        this.tenderedAmount = tenderedAmount;
        this.changeAmount = changeAmount;
        this.tipAmount = tipAmount;
        this.status = status;
        this.providerName = providerName;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public UUID getOrderId() { return orderId; }
    public UUID getPaymentMethodId() { return paymentMethodId; }
    public UUID getCashSessionId() { return cashSessionId; }
    public UUID getInitiatedByUserId() { return initiatedByUserId; }
    public UUID getLinkedPaymentId() { return linkedPaymentId; }
    public void setLinkedPaymentId(UUID linkedPaymentId) { this.linkedPaymentId = linkedPaymentId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public BigDecimal getTenderedAmount() { return tenderedAmount; }
    public BigDecimal getChangeAmount() { return changeAmount; }
    public BigDecimal getTipAmount() { return tipAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }
    public String getProviderTxId() { return providerTxId; }
    public void setProviderTxId(String providerTxId) { this.providerTxId = providerTxId; }
    public String getTerminalRef() { return terminalRef; }
    public void setTerminalRef(String terminalRef) { this.terminalRef = terminalRef; }
    public String getMaskedPan() { return maskedPan; }
    public void setMaskedPan(String maskedPan) { this.maskedPan = maskedPan; }
    public String getCardBrand() { return cardBrand; }
    public void setCardBrand(String cardBrand) { this.cardBrand = cardBrand; }
    public String getAuthCode() { return authCode; }
    public void setAuthCode(String authCode) { this.authCode = authCode; }
    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public boolean isReconciled() { return reconciled; }
    public void setReconciled(boolean reconciled) { this.reconciled = reconciled; }
    public Instant getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(Instant reconciledAt) { this.reconciledAt = reconciledAt; }
    public UUID getReconciledByUserId() { return reconciledByUserId; }
    public void setReconciledByUserId(UUID reconciledByUserId) { this.reconciledByUserId = reconciledByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
