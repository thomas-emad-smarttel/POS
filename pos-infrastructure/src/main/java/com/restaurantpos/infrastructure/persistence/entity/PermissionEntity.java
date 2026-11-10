package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "permissions")
public class PermissionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "module", nullable = false, length = 64)
    private String module;

    @Column(name = "description", nullable = false, length = 300)
    private String description;

    @Column(name = "sensitive", nullable = false)
    private boolean sensitive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PermissionEntity() {
    }

    public PermissionEntity(UUID id, String code, String module, String description, boolean sensitive) {
        this.id = id;
        this.code = code;
        this.module = module;
        this.description = description;
        this.sensitive = sensitive;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getModule() { return module; }
    public String getDescription() { return description; }
    public boolean isSensitive() { return sensitive; }
    public void setModule(String module) { this.module = module; }
    public void setDescription(String description) { this.description = description; }
    public void setSensitive(boolean sensitive) { this.sensitive = sensitive; }
}
