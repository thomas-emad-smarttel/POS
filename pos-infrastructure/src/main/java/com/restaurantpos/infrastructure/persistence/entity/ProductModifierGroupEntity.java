package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "product_modifier_groups")
public class ProductModifierGroupEntity {

    @Embeddable
    public static class ProductModifierGroupId implements Serializable {
        private static final long serialVersionUID = 1L;

        @Column(name = "product_id", nullable = false)
        private UUID productId;

        @Column(name = "modifier_group_id", nullable = false)
        private UUID modifierGroupId;

        protected ProductModifierGroupId() {
        }

        public ProductModifierGroupId(UUID productId, UUID modifierGroupId) {
            this.productId = productId;
            this.modifierGroupId = modifierGroupId;
        }

        public UUID getProductId() { return productId; }
        public UUID getModifierGroupId() { return modifierGroupId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ProductModifierGroupId that)) return false;
            return Objects.equals(productId, that.productId) && Objects.equals(modifierGroupId, that.modifierGroupId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(productId, modifierGroupId);
        }
    }

    @EmbeddedId
    private ProductModifierGroupId id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProductModifierGroupEntity() {
    }

    public ProductModifierGroupEntity(UUID productId, UUID modifierGroupId, UUID tenantId, int displayOrder) {
        this.id = new ProductModifierGroupId(productId, modifierGroupId);
        this.tenantId = tenantId;
        this.displayOrder = displayOrder;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public ProductModifierGroupId getId() { return id; }
    public int getDisplayOrder() { return displayOrder; }
}
