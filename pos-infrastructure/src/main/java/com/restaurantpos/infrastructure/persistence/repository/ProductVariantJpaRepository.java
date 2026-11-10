package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.ProductVariantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantJpaRepository extends JpaRepository<ProductVariantEntity, UUID> {
    Optional<ProductVariantEntity> findByIdAndProductIdAndTenantId(UUID id, UUID productId, UUID tenantId);
    Optional<ProductVariantEntity> findByTenantIdAndBarcode(UUID tenantId, String barcode);
    List<ProductVariantEntity> findByTenantIdAndProductIdOrderByDisplayOrderAscNameAsc(UUID tenantId, UUID productId);
}
