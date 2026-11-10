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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class TenantEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "default_currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String defaultCurrency;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected TenantEntity() {
    }

    public TenantEntity(UUID id, String code, String legalName, String displayName, String defaultCurrency, String status) {
        this.id = id;
        this.code = code;
        this.legalName = legalName;
        this.displayName = displayName;
        this.defaultCurrency = defaultCurrency;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getLegalName() { return legalName; }
    public String getDisplayName() { return displayName; }
    public String getDefaultCurrency() { return defaultCurrency; }
    public String getStatus() { return status; }
    public long getVersion() { return version; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setStatus(String status) { this.status = status; }
}
