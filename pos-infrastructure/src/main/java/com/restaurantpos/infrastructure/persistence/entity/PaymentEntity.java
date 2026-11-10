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
@Table(name = "payments")
public class PaymentEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "cash_session_id", updatable = false)
    private UUID cashSessionId;

    @Column(name = "payment_method_id", nullable = false, updatable = false)
    private UUID paymentMethodId;

    @Column(name = "processed_by_user_id", nullable = false, updatable = false)
    private UUID processedByUserId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", nullable = false, updatable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currencyCode;

    @Column(name = "amount", nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal amount;

    @Column(name = "tendered_amount", nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal tenderedAmount;

    @Column(name = "change_amount", nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal changeAmount;

    @Column(name = "tip_amount", nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal tipAmount;

    @Column(name = "status", nullable = false, updatable = false, length = 32)
    private String status;

    @Column(name = "external_reference", updatable = false, length = 128)
    private String externalReference;

    @Column(name = "masked_account", updatable = false, length = 32)
    private String maskedAccount;

    @Column(name = "paid_at", nullable = false, updatable = false)
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentEntity() {
    }

    public PaymentEntity(
            UUID id,
            UUID tenantId,
            UUID branchId,
            UUID orderId,
            UUID cashSessionId,
            UUID paymentMethodId,
            UUID processedByUserId,
            String idempotencyKey,
            String currencyCode,
            BigDecimal amount,
            BigDecimal tenderedAmount,
            BigDecimal changeAmount,
            BigDecimal tipAmount,
            String status,
            String externalReference,
            String maskedAccount,
            Instant paidAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.orderId = orderId;
        this.cashSessionId = cashSessionId;
        this.paymentMethodId = paymentMethodId;
        this.processedByUserId = processedByUserId;
        this.idempotencyKey = idempotencyKey;
        this.currencyCode = currencyCode;
        this.amount = amount;
        this.tenderedAmount = tenderedAmount;
        this.changeAmount = changeAmount;
        this.tipAmount = tipAmount;
        this.status = status;
        this.externalReference = externalReference;
        this.maskedAccount = maskedAccount;
        this.paidAt = paidAt;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public UUID getOrderId() { return orderId; }
    public UUID getCashSessionId() { return cashSessionId; }
    public UUID getPaymentMethodId() { return paymentMethodId; }
    public UUID getProcessedByUserId() { return processedByUserId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getTenderedAmount() { return tenderedAmount; }
    public BigDecimal getChangeAmount() { return changeAmount; }
    public BigDecimal getTipAmount() { return tipAmount; }
    public String getStatus() { return status; }
    public String getExternalReference() { return externalReference; }
    public String getMaskedAccount() { return maskedAccount; }
    public Instant getPaidAt() { return paidAt; }
}
