package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "drawer_events")
public class DrawerEventEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "terminal_id", updatable = false)
    private UUID terminalId;

    @Column(name = "cash_session_id", updatable = false)
    private UUID cashSessionId;

    @Column(name = "order_id", updatable = false)
    private UUID orderId;

    @Column(name = "payment_id", updatable = false)
    private UUID paymentId;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "approval_id", updatable = false)
    private UUID approvalId;

    @Column(name = "trigger_type", nullable = false, updatable = false, length = 32)
    private String triggerType;

    @Column(name = "reason", nullable = false, updatable = false, length = 300)
    private String reason;

    @Column(name = "hardware_device_name", nullable = false, updatable = false, length = 120)
    private String hardwareDeviceName;

    @Column(name = "hardware_status", nullable = false, updatable = false, length = 32)
    private String hardwareStatus;

    @Column(name = "opened_ok", nullable = false, updatable = false)
    private boolean openedOk;

    @Column(name = "error_message", updatable = false, length = 300)
    private String errorMessage;

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 64)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected DrawerEventEntity() {
    }

    public DrawerEventEntity(
            UUID id, UUID tenantId, UUID branchId, UUID terminalId, UUID cashSessionId,
            UUID orderId, UUID paymentId, UUID actorUserId, UUID approvalId,
            String triggerType, String reason, String hardwareDeviceName, String hardwareStatus,
            boolean openedOk, String errorMessage, String correlationId, Instant occurredAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.terminalId = terminalId;
        this.cashSessionId = cashSessionId;
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.actorUserId = actorUserId;
        this.approvalId = approvalId;
        this.triggerType = triggerType;
        this.reason = reason;
        this.hardwareDeviceName = hardwareDeviceName;
        this.hardwareStatus = hardwareStatus;
        this.openedOk = openedOk;
        this.errorMessage = errorMessage;
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public UUID getTerminalId() { return terminalId; }
    public UUID getCashSessionId() { return cashSessionId; }
    public UUID getOrderId() { return orderId; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getActorUserId() { return actorUserId; }
    public UUID getApprovalId() { return approvalId; }
    public String getTriggerType() { return triggerType; }
    public String getReason() { return reason; }
    public String getHardwareDeviceName() { return hardwareDeviceName; }
    public String getHardwareStatus() { return hardwareStatus; }
    public boolean isOpenedOk() { return openedOk; }
    public String getErrorMessage() { return errorMessage; }
    public String getCorrelationId() { return correlationId; }
    public Instant getOccurredAt() { return occurredAt; }
}
