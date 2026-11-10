package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.domain.menu.ProductCatalogView;
import com.restaurantpos.infrastructure.persistence.entity.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID> {

    Optional<ProductEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<ProductEntity> findByTenantIdAndBarcode(UUID tenantId, String barcode);

    @Query("""
            SELECT p
            FROM ProductEntity p
            WHERE p.tenantId = :tenantId
              AND (
                   LOWER(p.name) LIKE :pattern
                   OR LOWER(p.sku) LIKE :pattern
                   OR (p.barcode IS NOT NULL AND LOWER(p.barcode) LIKE :pattern)
              )
            ORDER BY p.displayOrder ASC, p.name ASC
            """)
    Page<ProductEntity> searchByTenant(
            @Param("tenantId") UUID tenantId,
            @Param("pattern") String pattern,
            Pageable pageable
    );

    @Query("""
            SELECT p
            FROM ProductEntity p
            WHERE p.tenantId = :tenantId
              AND p.categoryId = :categoryId
              AND (
                   LOWER(p.name) LIKE :pattern
                   OR LOWER(p.sku) LIKE :pattern
                   OR (p.barcode IS NOT NULL AND LOWER(p.barcode) LIKE :pattern)
              )
            ORDER BY p.displayOrder ASC, p.name ASC
            """)
    Page<ProductEntity> searchByTenantAndCategory(
            @Param("tenantId") UUID tenantId,
            @Param("categoryId") UUID categoryId,
            @Param("pattern") String pattern,
            Pageable pageable
    );

    @Query("""
            SELECT new com.restaurantpos.domain.menu.ProductCatalogView(
                p.id,
                p.sku,
                p.name,
                c.name,
                COALESCE(o.priceOverride, p.basePrice),
                COALESCE(o.available, p.active)
            )
            FROM ProductEntity p
            JOIN CategoryEntity c ON c.id = p.categoryId AND c.tenantId = p.tenantId
            LEFT JOIN BranchProductOverrideEntity o
                ON o.productId = p.id AND o.tenantId = p.tenantId AND o.branchId = :branchId
            WHERE p.tenantId = :tenantId
              AND p.active = true
              AND c.active = true
            ORDER BY c.displayOrder ASC, p.name ASC
            """)
    Page<ProductCatalogView> findBranchCatalog(
            @Param("tenantId") UUID tenantId,
            @Param("branchId") UUID branchId,
            Pageable pageable
    );
}
