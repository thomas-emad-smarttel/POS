package com.restaurantpos.domain.port;

import com.restaurantpos.domain.menu.BranchProductOverride;
import com.restaurantpos.domain.menu.Category;
import com.restaurantpos.domain.menu.Modifier;
import com.restaurantpos.domain.menu.ModifierGroup;
import com.restaurantpos.domain.menu.Product;
import com.restaurantpos.domain.menu.ProductVariant;
import com.restaurantpos.domain.menu.TaxDefinition;
import com.restaurantpos.shared.dto.pos.CategoryDto;
import com.restaurantpos.shared.dto.pos.ProductDto;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuRepositoryPort {
    TaxDefinition saveTax(TaxDefinition tax);
    List<TaxDefinition> findTaxesByTenant(TenantId tenantId);
    Optional<TaxDefinition> findTaxScoped(UUID taxId, TenantId tenantId);

    Category saveCategory(Category category);
    Optional<Category> findCategoryScoped(UUID categoryId, TenantId tenantId);
    void upsertBranchCategoryVisibility(TenantId tenantId, BranchId branchId, UUID categoryId, boolean visible, Integer displayOrderOverride);
    List<CategoryDto> findBranchCategories(TenantId tenantId, BranchId branchId);

    ModifierGroup saveModifierGroup(ModifierGroup group);
    Optional<ModifierGroup> findModifierGroupScoped(UUID groupId, TenantId tenantId);
    List<ModifierGroup> findModifierGroupsByTenant(TenantId tenantId);
    Modifier saveModifier(Modifier modifier);
    List<ModifierGroup> findModifierGroupsForProduct(UUID productId, TenantId tenantId);

    Product saveProduct(Product product);
    Optional<Product> findProductScoped(UUID productId, TenantId tenantId);
    ProductVariant saveVariant(ProductVariant variant);
    Optional<ProductVariant> findVariantScoped(UUID variantId, UUID productId, TenantId tenantId);
    List<ProductVariant> findVariantsForProduct(UUID productId, TenantId tenantId);

    BranchProductOverride upsertBranchProductOverride(BranchProductOverride override);
    Optional<BranchProductOverride> findBranchProductOverride(TenantId tenantId, BranchId branchId, UUID productId);

    List<ProductDto> searchBranchProducts(TenantId tenantId, BranchId branchId, UUID categoryId, String query, int page, int size);
    Optional<ProductDto> findBranchProductDetail(TenantId tenantId, BranchId branchId, UUID productId);
    Optional<ProductDto> findBranchProductByBarcode(TenantId tenantId, BranchId branchId, String barcode);
}
