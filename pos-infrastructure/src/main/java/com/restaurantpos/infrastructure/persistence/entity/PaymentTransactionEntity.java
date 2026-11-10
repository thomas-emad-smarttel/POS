package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "payment_transactions")
public class PaymentTransactionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "payment_id", updatable = false)
    private UUID paymentId;

    @Column(name = "payment_attempt_id", updatable = false)
    private UUID paymentAttemptId;

    @Column(name = "transaction_type", nullable = false, updatable = false, length = 32)
    private String transactionType;

    @Column(name = "amount", nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal amount;

    @Column(name = "gateway_name", nullable = false, updatable = false, length = 64)
    private String gatewayName;

    @Column(name = "gateway_tx_ref", updatable = false, length = 128)
    private String gatewayTxRef;

    @Column(name = "auth_code", updatable = false, length = 64)
    private String authCode;

    @Column(name = "response_code", updatable = false, length = 32)
    private String responseCode;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected PaymentTransactionEntity() {
    }

    public PaymentTransactionEntity(
            UUID id, UUID tenantId, UUID branchId, UUID paymentId, UUID paymentAttemptId,
            String transactionType, BigDecimal amount, String gatewayName,
            String gatewayTxRef, String authCode, String responseCode, Instant occurredAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.paymentId = paymentId;
        this.paymentAttemptId = paymentAttemptId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.gatewayName = gatewayName;
        this.gatewayTxRef = gatewayTxRef;
        this.authCode = authCode;
        this.responseCode = responseCode;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getPaymentAttemptId() { return paymentAttemptId; }
    public String getTransactionType() { return transactionType; }
    public BigDecimal getAmount() { return amount; }
    public String getGatewayName() { return gatewayName; }
    public String getGatewayTxRef() { return gatewayTxRef; }
    public String getAuthCode() { return authCode; }
    public String getResponseCode() { return responseCode; }
    public Instant getOccurredAt() { return occurredAt; }
}
