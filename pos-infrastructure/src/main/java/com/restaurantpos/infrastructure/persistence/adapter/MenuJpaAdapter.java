package com.restaurantpos.infrastructure.persistence.adapter;

import com.restaurantpos.domain.menu.BranchProductOverride;
import com.restaurantpos.domain.menu.Category;
import com.restaurantpos.domain.menu.Modifier;
import com.restaurantpos.domain.menu.ModifierGroup;
import com.restaurantpos.domain.menu.Product;
import com.restaurantpos.domain.menu.ProductVariant;
import com.restaurantpos.domain.menu.TaxDefinition;
import com.restaurantpos.domain.port.MenuRepositoryPort;
import com.restaurantpos.infrastructure.persistence.DatabaseExceptionTranslator;
import com.restaurantpos.infrastructure.persistence.entity.BranchCategoryOverrideEntity;
import com.restaurantpos.infrastructure.persistence.entity.BranchProductOverrideEntity;
import com.restaurantpos.infrastructure.persistence.entity.CategoryEntity;
import com.restaurantpos.infrastructure.persistence.entity.ModifierEntity;
import com.restaurantpos.infrastructure.persistence.entity.ModifierGroupEntity;
import com.restaurantpos.infrastructure.persistence.entity.ProductEntity;
import com.restaurantpos.infrastructure.persistence.entity.ProductModifierGroupEntity;
import com.restaurantpos.infrastructure.persistence.entity.ProductVariantEntity;
import com.restaurantpos.infrastructure.persistence.entity.TaxEntity;
import com.restaurantpos.infrastructure.persistence.repository.BranchCategoryOverrideJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.BranchProductOverrideJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.CategoryJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.ModifierGroupJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.ModifierJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.ProductJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.ProductModifierGroupJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.ProductVariantJpaRepository;
import java.util.Objects;
import com.restaurantpos.infrastructure.persistence.repository.TaxJpaRepository;
import com.restaurantpos.shared.dto.pos.CategoryDto;
import com.restaurantpos.shared.dto.pos.ModifierDto;
import com.restaurantpos.shared.dto.pos.ModifierGroupDto;
import com.restaurantpos.shared.dto.pos.ProductDto;
import com.restaurantpos.shared.dto.pos.ProductVariantDto;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class MenuJpaAdapter implements MenuRepositoryPort {

    private final TaxJpaRepository taxRepo;
    private final CategoryJpaRepository categoryRepo;
    private final BranchCategoryOverrideJpaRepository categoryOverrideRepo;
    private final ProductJpaRepository productRepo;
    private final ProductVariantJpaRepository variantRepo;
    private final ModifierGroupJpaRepository modifierGroupRepo;
    private final ModifierJpaRepository modifierRepo;
    private final ProductModifierGroupJpaRepository productModifierGroupRepo;
    private final BranchProductOverrideJpaRepository productOverrideRepo;
    private final DatabaseExceptionTranslator translator;

    public MenuJpaAdapter(
            TaxJpaRepository taxRepo,
            CategoryJpaRepository categoryRepo,
            BranchCategoryOverrideJpaRepository categoryOverrideRepo,
            ProductJpaRepository productRepo,
            ProductVariantJpaRepository variantRepo,
            ModifierGroupJpaRepository modifierGroupRepo,
            ModifierJpaRepository modifierRepo,
            ProductModifierGroupJpaRepository productModifierGroupRepo,
            BranchProductOverrideJpaRepository productOverrideRepo,
            DatabaseExceptionTranslator translator
    ) {
        this.taxRepo = taxRepo;
        this.categoryRepo = categoryRepo;
        this.categoryOverrideRepo = categoryOverrideRepo;
        this.productRepo = productRepo;
        this.variantRepo = variantRepo;
        this.modifierGroupRepo = modifierGroupRepo;
        this.modifierRepo = modifierRepo;
        this.productModifierGroupRepo = productModifierGroupRepo;
        this.productOverrideRepo = productOverrideRepo;
        this.translator = translator;
    }

    @Override
    @Transactional
    public TaxDefinition saveTax(TaxDefinition tax) {
        TaxEntity entity = taxRepo.findByIdAndTenantId(tax.id(), tax.tenantId().value())
                .map(existing -> {
                    existing.setName(tax.name());
                    existing.setRatePercent(tax.ratePercent());
                    existing.setInclusive(tax.inclusive());
                    existing.setActive(tax.active());
                    return existing;
                })
                .orElseGet(() -> new TaxEntity(
                        tax.id(), tax.tenantId().value(), tax.code(), tax.name(),
                        tax.ratePercent(), tax.inclusive(), tax.active()
                ));
        TaxEntity saved = translator.execute("save tax", () -> taxRepo.saveAndFlush(entity));
        return toTaxDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxDefinition> findTaxesByTenant(TenantId tenantId) {
        return taxRepo.findByTenantIdOrderByNameAsc(tenantId.value()).stream()
                .map(MenuJpaAdapter::toTaxDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaxDefinition> findTaxScoped(UUID taxId, TenantId tenantId) {
        return taxRepo.findByIdAndTenantId(taxId, tenantId.value()).map(MenuJpaAdapter::toTaxDomain);
    }

    @Override
    @Transactional
    public Category saveCategory(Category category) {
        CategoryEntity entity = categoryRepo.findByIdAndTenantId(category.id(), category.tenantId().value())
                .map(existing -> {
                    existing.setParentId(category.parentId());
                    existing.setCode(category.code());
                    existing.setName(category.name());
                    existing.setDisplayOrder(category.displayOrder());
                    existing.setColorHex(category.colorHex());
                    existing.setActive(category.active());
                    return existing;
                })
                .orElseGet(() -> {
                    CategoryEntity created = new CategoryEntity(
                            category.id(), category.tenantId().value(), category.code(),
                            category.name(), category.displayOrder(), category.active()
                    );
                    created.setParentId(category.parentId());
                    created.setColorHex(category.colorHex());
                    return created;
                });
        CategoryEntity saved = translator.execute("save category", () -> categoryRepo.saveAndFlush(entity));
        return toCategoryDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> findCategoryScoped(UUID categoryId, TenantId tenantId) {
        return categoryRepo.findByIdAndTenantId(categoryId, tenantId.value()).map(MenuJpaAdapter::toCategoryDomain);
    }

    @Override
    @Transactional
    public void upsertBranchCategoryVisibility(
            TenantId tenantId,
            BranchId branchId,
            UUID categoryId,
            boolean visible,
            Integer displayOrderOverride
    ) {
        BranchCategoryOverrideEntity entity = categoryOverrideRepo
                .findByTenantIdAndBranchIdAndCategoryId(tenantId.value(), branchId.value(), categoryId)
                .map(existing -> {
                    existing.setVisible(visible);
                    existing.setDisplayOrderOverride(displayOrderOverride);
                    return existing;
                })
                .orElseGet(() -> new BranchCategoryOverrideEntity(
                        UUID.randomUUID(), tenantId.value(), branchId.value(), categoryId, visible, displayOrderOverride
                ));
        translator.execute("save branch category visibility", () -> categoryOverrideRepo.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryDto> findBranchCategories(TenantId tenantId, BranchId branchId) {
        Map<UUID, BranchCategoryOverrideEntity> overrides = categoryOverrideRepo
                .findByTenantIdAndBranchId(tenantId.value(), branchId.value()).stream()
                .collect(Collectors.toMap(BranchCategoryOverrideEntity::getCategoryId, Function.identity()));

        return categoryRepo.findByTenantIdOrderByDisplayOrderAscNameAsc(tenantId.value()).stream()
                .map(c -> {
                    BranchCategoryOverrideEntity ov = overrides.get(c.getId());
                    boolean visible = ov == null || ov.isVisible();
                    int order = ov != null && ov.getDisplayOrderOverride() != null
                            ? ov.getDisplayOrderOverride()
                            : c.getDisplayOrder();
                    return new CategoryDto(
                            c.getId(), c.getParentId(), c.getCode(), c.getName(),
                            order, c.getColorHex(), c.isActive(), visible, c.getVersion()
                    );
                })
                .sorted(Comparator.comparingInt(CategoryDto::displayOrder).thenComparing(CategoryDto::name))
                .toList();
    }

    @Override
    @Transactional
    public ModifierGroup saveModifierGroup(ModifierGroup group) {
        ModifierGroupEntity entity = modifierGroupRepo.findByIdAndTenantId(group.id(), group.tenantId().value())
                .map(existing -> {
                    existing.setName(group.name());
                    existing.setMinSelections(group.minSelections());
                    existing.setMaxSelections(group.maxSelections());
                    existing.setRequired(group.required());
                    existing.setActive(group.active());
                    return existing;
                })
                .orElseGet(() -> new ModifierGroupEntity(
                        group.id(), group.tenantId().value(), group.code(), group.name(),
                        group.minSelections(), group.maxSelections(), group.required(), group.active()
                ));
        ModifierGroupEntity saved = translator.execute("save modifier group", () -> modifierGroupRepo.saveAndFlush(entity));
        return toModifierGroupDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ModifierGroup> findModifierGroupScoped(UUID groupId, TenantId tenantId) {
        return modifierGroupRepo.findByIdAndTenantId(groupId, tenantId.value()).map(this::toModifierGroupDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModifierGroup> findModifierGroupsByTenant(TenantId tenantId) {
        return modifierGroupRepo.findByTenantIdOrderByNameAsc(tenantId.value()).stream()
                .map(this::toModifierGroupDomain)
                .toList();
    }

    @Override
    @Transactional
    public Modifier saveModifier(Modifier modifier) {
        ModifierEntity entity = modifierRepo.findById(modifier.id())
                .map(existing -> {
                    existing.setName(modifier.name());
                    existing.setPriceDelta(modifier.priceDelta());
                    existing.setDisplayOrder(modifier.displayOrder());
                    existing.setDefaultSelected(modifier.defaultSelected());
                    existing.setActive(modifier.active());
                    return existing;
                })
                .orElseGet(() -> new ModifierEntity(
                        modifier.id(), modifier.tenantId().value(), modifier.modifierGroupId(),
                        modifier.code(), modifier.name(), modifier.priceDelta(),
                        modifier.displayOrder(), modifier.defaultSelected(), modifier.active()
                ));
        ModifierEntity saved = translator.execute("save modifier", () -> modifierRepo.saveAndFlush(entity));
        return toModifierDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModifierGroup> findModifierGroupsForProduct(UUID productId, TenantId tenantId) {
        return productModifierGroupRepo.findByIdProductIdAndTenantIdOrderByDisplayOrderAsc(productId, tenantId.value()).stream()
                .map(link -> modifierGroupRepo.findByIdAndTenantId(link.getId().getModifierGroupId(), tenantId.value()).orElse(null))
                .filter(Objects::nonNull)
                .map(this::toModifierGroupDomain)
                .toList();
    }

    @Override
    @Transactional
    public Product saveProduct(Product product) {
        ProductEntity entity = productRepo.findByIdAndTenantId(product.id(), product.tenantId().value())
                .map(existing -> {
                    existing.setCategoryId(product.categoryId());
                    existing.setTaxId(product.taxId());
                    existing.setSku(product.sku());
                    existing.setBarcode(product.barcode());
                    existing.setName(product.name());
                    existing.setDescription(product.description());
                    existing.setBasePrice(product.basePrice());
                    existing.setPricingMode(product.pricingMode());
                    existing.setProductType(product.productType());
                    existing.setImageRef(product.imageRef());
                    existing.setDisplayOrder(product.displayOrder());
                    existing.setActive(product.active());
                    return existing;
                })
                .orElseGet(() -> {
                    ProductEntity created = new ProductEntity(
                            product.id(), product.tenantId().value(), product.categoryId(),
                            product.sku(), product.name(), product.basePrice()
                    );
                    created.setTaxId(product.taxId());
                    created.setBarcode(product.barcode());
                    created.setDescription(product.description());
                    created.setPricingMode(product.pricingMode());
                    created.setProductType(product.productType());
                    created.setImageRef(product.imageRef());
                    created.setDisplayOrder(product.displayOrder());
                    created.setActive(product.active());
                    return created;
                });

        ProductEntity saved = translator.execute("save product", () -> productRepo.saveAndFlush(entity));

        productModifierGroupRepo.deleteByIdProductIdAndTenantId(saved.getId(), product.tenantId().value());
        productModifierGroupRepo.flush();
        for (int i = 0; i < product.modifierGroupIds().size(); i++) {
            UUID groupId = product.modifierGroupIds().get(i);
            productModifierGroupRepo.save(new ProductModifierGroupEntity(saved.getId(), groupId, product.tenantId().value(), i));
        }
        productModifierGroupRepo.flush();

        return toProductDomain(saved, product.modifierGroupIds());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findProductScoped(UUID productId, TenantId tenantId) {
        return productRepo.findByIdAndTenantId(productId, tenantId.value())
                .map(p -> {
                    List<UUID> groupIds = productModifierGroupRepo
                            .findByIdProductIdAndTenantIdOrderByDisplayOrderAsc(p.getId(), tenantId.value())
                            .stream()
                            .map(l -> l.getId().getModifierGroupId())
                            .toList();
                    return toProductDomain(p, groupIds);
                });
    }

    @Override
    @Transactional
    public ProductVariant saveVariant(ProductVariant variant) {
        ProductVariantEntity entity = variantRepo.findByIdAndProductIdAndTenantId(
                        variant.id(), variant.productId(), variant.tenantId().value())
                .map(existing -> {
                    existing.setName(variant.name());
                    existing.setBarcode(variant.barcode());
                    existing.setPriceOverride(variant.priceOverride());
                    existing.setDisplayOrder(variant.displayOrder());
                    existing.setActive(variant.active());
                    return existing;
                })
                .orElseGet(() -> new ProductVariantEntity(
                        variant.id(), variant.tenantId().value(), variant.productId(),
                        variant.sku(), variant.barcode(), variant.name(),
                        variant.priceOverride(), variant.displayOrder(), variant.active()
                ));
        ProductVariantEntity saved = translator.execute("save product variant", () -> variantRepo.saveAndFlush(entity));
        return toVariantDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductVariant> findVariantScoped(UUID variantId, UUID productId, TenantId tenantId) {
        return variantRepo.findByIdAndProductIdAndTenantId(variantId, productId, tenantId.value())
                .map(MenuJpaAdapter::toVariantDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariant> findVariantsForProduct(UUID productId, TenantId tenantId) {
        return variantRepo.findByTenantIdAndProductIdOrderByDisplayOrderAscNameAsc(tenantId.value(), productId)
                .stream()
                .map(MenuJpaAdapter::toVariantDomain)
                .toList();
    }

    @Override
    @Transactional
    public BranchProductOverride upsertBranchProductOverride(BranchProductOverride override) {
        BranchProductOverrideEntity entity = productOverrideRepo
                .findByTenantIdAndBranchIdAndProductId(override.tenantId().value(), override.branchId().value(), override.productId())
                .map(existing -> {
                    existing.setPriceOverride(override.priceOverride());
                    existing.setTaxOverrideId(override.taxOverrideId());
                    existing.setAvailable(override.available());
                    return existing;
                })
                .orElseGet(() -> {
                    BranchProductOverrideEntity created = new BranchProductOverrideEntity(
                            override.id(), override.tenantId().value(), override.branchId().value(),
                            override.productId(), override.priceOverride(), override.available()
                    );
                    created.setTaxOverrideId(override.taxOverrideId());
                    return created;
                });
        BranchProductOverrideEntity saved = translator.execute("save branch product override", () -> productOverrideRepo.saveAndFlush(entity));
        return new BranchProductOverride(
                saved.getId(), TenantId.of(saved.getTenantId()), BranchId.of(saved.getBranchId()),
                saved.getProductId(), saved.getPriceOverride(), saved.getTaxOverrideId(),
                saved.isAvailable(), saved.getVersion()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BranchProductOverride> findBranchProductOverride(TenantId tenantId, BranchId branchId, UUID productId) {
        return productOverrideRepo.findByTenantIdAndBranchIdAndProductId(tenantId.value(), branchId.value(), productId)
                .map(saved -> new BranchProductOverride(
                        saved.getId(), TenantId.of(saved.getTenantId()), BranchId.of(saved.getBranchId()),
                        saved.getProductId(), saved.getPriceOverride(), saved.getTaxOverrideId(),
                        saved.isAvailable(), saved.getVersion()
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDto> searchBranchProducts(
            TenantId tenantId,
            BranchId branchId,
            UUID categoryId,
            String query,
            int page,
            int size
    ) {
        String pattern = (query == null || query.isBlank())
                ? "%"
                : "%" + query.trim().toLowerCase(java.util.Locale.ROOT) + "%";
        Map<UUID, CategoryEntity> categories = categoryRepo.findByTenantIdOrderByDisplayOrderAscNameAsc(tenantId.value())
                .stream().collect(Collectors.toMap(CategoryEntity::getId, Function.identity()));
        Map<UUID, BranchProductOverrideEntity> overrides = productOverrideRepo
                .findByTenantIdAndBranchId(tenantId.value(), branchId.value())
                .stream().collect(Collectors.toMap(BranchProductOverrideEntity::getProductId, Function.identity()));

        var pageResult = categoryId == null
                ? productRepo.searchByTenant(tenantId.value(), pattern, PageRequest.of(page, size))
                : productRepo.searchByTenantAndCategory(tenantId.value(), categoryId, pattern, PageRequest.of(page, size));
        return pageResult
                .stream()
                .map(p -> toProductDto(p, categories.get(p.getCategoryId()), overrides.get(p.getId()), tenantId))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductDto> findBranchProductDetail(TenantId tenantId, BranchId branchId, UUID productId) {
        return productRepo.findByIdAndTenantId(productId, tenantId.value())
                .map(p -> {
                    CategoryEntity cat = categoryRepo.findByIdAndTenantId(p.getCategoryId(), tenantId.value()).orElse(null);
                    BranchProductOverrideEntity ov = productOverrideRepo
                            .findByTenantIdAndBranchIdAndProductId(tenantId.value(), branchId.value(), p.getId())
                            .orElse(null);
                    return toProductDto(p, cat, ov, tenantId);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductDto> findBranchProductByBarcode(TenantId tenantId, BranchId branchId, String barcode) {
        Optional<ProductEntity> direct = productRepo.findByTenantIdAndBarcode(tenantId.value(), barcode);
        if (direct.isPresent()) {
            return findBranchProductDetail(tenantId, branchId, direct.get().getId());
        }
        return variantRepo.findByTenantIdAndBarcode(tenantId.value(), barcode)
                .flatMap(v -> findBranchProductDetail(tenantId, branchId, v.getProductId()));
    }

    private ProductDto toProductDto(
            ProductEntity p,
            CategoryEntity cat,
            BranchProductOverrideEntity ov,
            TenantId tenantId
    ) {
        BigDecimal effectivePrice = (ov != null && ov.getPriceOverride() != null) ? ov.getPriceOverride() : p.getBasePrice();
        boolean branchAvailable = p.isActive() && (ov == null || ov.isAvailable());
        UUID effectiveTaxId = (ov != null && ov.getTaxOverrideId() != null) ? ov.getTaxOverrideId() : p.getTaxId();

        List<ProductVariantDto> variants = findVariantsForProduct(p.getId(), tenantId).stream()
                .map(v -> new ProductVariantDto(
                        v.id(), v.productId(), v.sku(), v.barcode(), v.name(),
                        v.priceOverride(), v.displayOrder(), v.active(), v.version()
                ))
                .toList();

        List<ModifierGroupDto> groups = findModifierGroupsForProduct(p.getId(), tenantId).stream()
                .map(g -> new ModifierGroupDto(
                        g.id(), g.code(), g.name(), g.minSelections(), g.maxSelections(),
                        g.required(), g.active(),
                        g.modifiers().stream().map(m -> new ModifierDto(
                                m.id(), m.modifierGroupId(), m.code(), m.name(), m.priceDelta(),
                                m.displayOrder(), m.defaultSelected(), m.active(), m.version()
                        )).toList(),
                        g.version()
                ))
                .toList();

        return new ProductDto(
                p.getId(), p.getCategoryId(), cat != null ? cat.getName() : "",
                effectiveTaxId, p.getSku(), p.getBarcode(), p.getName(), p.getDescription(),
                p.getBasePrice(), effectivePrice, p.getPricingMode(), p.getProductType(),
                p.getImageRef(), p.getDisplayOrder(), p.isActive(), branchAvailable,
                variants, groups, p.getVersion()
        );
    }

    private ModifierGroup toModifierGroupDomain(ModifierGroupEntity entity) {
        List<Modifier> mods = modifierRepo.findByTenantIdAndModifierGroupIdOrderByDisplayOrderAscNameAsc(
                        entity.getTenantId(), entity.getId())
                .stream()
                .map(MenuJpaAdapter::toModifierDomain)
                .toList();
        return new ModifierGroup(
                entity.getId(), TenantId.of(entity.getTenantId()), entity.getCode(), entity.getName(),
                entity.getMinSelections(), entity.getMaxSelections(), entity.isRequired(), entity.isActive(),
                mods, entity.getVersion()
        );
    }

    private static Modifier toModifierDomain(ModifierEntity e) {
        return new Modifier(
                e.getId(), TenantId.of(e.getTenantId()), e.getModifierGroupId(), e.getCode(), e.getName(),
                e.getPriceDelta(), e.getDisplayOrder(), e.isDefaultSelected(), e.isActive(), e.getVersion()
        );
    }

    private static TaxDefinition toTaxDomain(TaxEntity e) {
        return new TaxDefinition(
                e.getId(), TenantId.of(e.getTenantId()), e.getCode(), e.getName(),
                e.getRatePercent(), e.isInclusive(), e.isActive(), e.getVersion()
        );
    }

    private static Category toCategoryDomain(CategoryEntity e) {
        return new Category(
                e.getId(), TenantId.of(e.getTenantId()), e.getParentId(), e.getCode(), e.getName(),
                e.getDisplayOrder(), e.getColorHex(), e.isActive(), e.getVersion()
        );
    }

    private static Product toProductDomain(ProductEntity p, List<UUID> groupIds) {
        return new Product(
                p.getId(), TenantId.of(p.getTenantId()), p.getCategoryId(), p.getTaxId(),
                p.getSku(), p.getBarcode(), p.getName(), p.getDescription(),
                p.getBasePrice(), p.getPricingMode(), p.getProductType(), p.getImageRef(),
                p.getDisplayOrder(), p.isActive(), groupIds, p.getVersion()
        );
    }

    private static ProductVariant toVariantDomain(ProductVariantEntity e) {
        return new ProductVariant(
                e.getId(), TenantId.of(e.getTenantId()), e.getProductId(), e.getSku(), e.getBarcode(),
                e.getName(), e.getPriceOverride(), e.getDisplayOrder(), e.isActive(), e.getVersion()
        );
    }
}
