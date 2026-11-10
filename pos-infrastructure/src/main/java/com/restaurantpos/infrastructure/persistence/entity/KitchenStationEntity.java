package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kitchen_stations")
public class KitchenStationEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "station_type", nullable = false, length = 32)
    private String stationType;

    @Column(name = "printer_device_id")
    private UUID printerDeviceId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "expected_prep_minutes", nullable = false)
    private int expectedPrepMinutes;

    @Column(name = "expo_enabled", nullable = false)
    private boolean expoEnabled;

    @Column(name = "paper_width", nullable = false)
    private int paperWidth;

    @Column(name = "receipt_language", nullable = false, length = 16)
    private String receiptLanguage;

    @Column(name = "receipt_encoding", nullable = false, length = 32)
    private String receiptEncoding;

    @Column(name = "default_station", nullable = false)
    private boolean defaultStation;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected KitchenStationEntity() {
    }

    public KitchenStationEntity(UUID id, UUID tenantId, UUID branchId, String code, String name) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.code = code;
        this.name = name;
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
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStationType() { return stationType; }
    public void setStationType(String stationType) { this.stationType = stationType; }
    public UUID getPrinterDeviceId() { return printerDeviceId; }
    public void setPrinterDeviceId(UUID printerDeviceId) { this.printerDeviceId = printerDeviceId; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public int getExpectedPrepMinutes() { return expectedPrepMinutes; }
    public void setExpectedPrepMinutes(int expectedPrepMinutes) { this.expectedPrepMinutes = expectedPrepMinutes; }
    public boolean isExpoEnabled() { return expoEnabled; }
    public void setExpoEnabled(boolean expoEnabled) { this.expoEnabled = expoEnabled; }
    public int getPaperWidth() { return paperWidth; }
    public void setPaperWidth(int paperWidth) { this.paperWidth = paperWidth; }
    public String getReceiptLanguage() { return receiptLanguage; }
    public void setReceiptLanguage(String receiptLanguage) { this.receiptLanguage = receiptLanguage; }
    public String getReceiptEncoding() { return receiptEncoding; }
    public void setReceiptEncoding(String receiptEncoding) { this.receiptEncoding = receiptEncoding; }
    public boolean isDefaultStation() { return defaultStation; }
    public void setDefaultStation(boolean defaultStation) { this.defaultStation = defaultStation; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public long getVersion() { return version; }
}
