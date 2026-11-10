package com.restaurantpos.application.pos;

import com.restaurantpos.application.audit.AuditRecordingService;
import com.restaurantpos.application.security.PermissionPolicy;
import com.restaurantpos.domain.exception.DomainException;
import com.restaurantpos.domain.exception.ResourceNotFoundException;
import com.restaurantpos.domain.exception.UnauthorizedOperationException;
import com.restaurantpos.domain.menu.BranchProductOverride;
import com.restaurantpos.domain.menu.Category;
import com.restaurantpos.domain.menu.Modifier;
import com.restaurantpos.domain.menu.ModifierGroup;
import com.restaurantpos.domain.menu.Product;
import com.restaurantpos.domain.menu.ProductVariant;
import com.restaurantpos.domain.menu.TaxDefinition;
import com.restaurantpos.domain.port.BranchEventPublishPort;
import com.restaurantpos.domain.port.MenuRepositoryPort;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.shared.dto.pos.BranchCategoryVisibilityRequest;
import com.restaurantpos.shared.dto.pos.BranchProductOverrideRequest;
import com.restaurantpos.shared.dto.pos.CategoryDto;
import com.restaurantpos.shared.dto.pos.CreateTaxRequest;
import com.restaurantpos.shared.dto.pos.ModifierDto;
import com.restaurantpos.shared.dto.pos.ModifierGroupDto;
import com.restaurantpos.shared.dto.pos.ProductDto;
import com.restaurantpos.shared.dto.pos.ProductVariantDto;
import com.restaurantpos.shared.dto.pos.SaveCategoryRequest;
import com.restaurantpos.shared.dto.pos.SaveModifierGroupRequest;
import com.restaurantpos.shared.dto.pos.SaveModifierRequest;
import com.restaurantpos.shared.dto.pos.SaveProductRequest;
import com.restaurantpos.shared.dto.pos.SaveProductVariantRequest;
import com.restaurantpos.shared.dto.pos.TaxDto;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.security.AuditAction;
import com.restaurantpos.shared.security.PermissionCode;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Application service for tenant-centralized menu management and branch-specific overrides.
 */
public class MenuManagementService {

    private final MenuRepositoryPort menuRepo;
    private final AuditRecordingService auditService;
    private final BranchEventPublishPort eventPublisher;

    public MenuManagementService(
            MenuRepositoryPort menuRepo,
            AuditRecordingService auditService,
            BranchEventPublishPort eventPublisher
    ) {
        this.menuRepo = Objects.requireNonNull(menuRepo);
        this.auditService = Objects.requireNonNull(auditService);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    public List<TaxDto> listTaxes(AccessPrincipal actor) {
        requirePermission(actor, PermissionCode.MENU_VIEW);
        return menuRepo.findTaxesByTenant(actor.tenantId()).stream()
                .map(t -> new TaxDto(t.id(), t.code(), t.name(), t.ratePercent(), t.inclusive(), t.active(), t.version()))
                .toList();
    }

    public TaxDto createTax(AccessPrincipal actor, CreateTaxRequest req, CorrelationId corr) {
        requireManagePermission(actor);
        TaxDefinition saved = menuRepo.saveTax(new TaxDefinition(
                UUID.randomUUID(), actor.tenantId(), req.code().trim(), req.name().trim(),
                req.ratePercent(), req.inclusive(), req.active(), 0L
        ));
        audit(actor, AuditAction.MENU_CREATED, "Tax", saved.id().toString(), null, corr);
        return new TaxDto(saved.id(), saved.code(), saved.name(), saved.ratePercent(), saved.inclusive(), saved.active(), saved.version());
    }

    public List<CategoryDto> listBranchCategories(AccessPrincipal actor) {
        requirePermission(actor, PermissionCode.MENU_VIEW);
        return menuRepo.findBranchCategories(actor.tenantId(), requireActiveBranch(actor));
    }

    public CategoryDto createCategory(AccessPrincipal actor, SaveCategoryRequest req, CorrelationId corr) {
        requireManagePermission(actor);
        Category saved = menuRepo.saveCategory(new Category(
                UUID.randomUUID(), actor.tenantId(), req.parentId(), req.code().trim(), req.name().trim(),
                req.displayOrder(), req.colorHex(), req.active(), 0L
        ));
        audit(actor, AuditAction.MENU_CREATED, "Category", saved.id().toString(), null, corr);
        publishIfBranchActive(actor, "MENU_UPDATED", saved.id());
        return new CategoryDto(saved.id(), saved.parentId(), saved.code(), saved.name(), saved.displayOrder(), saved.colorHex(), saved.active(), true, saved.version());
    }

    public CategoryDto updateCategory(AccessPrincipal actor, UUID categoryId, SaveCategoryRequest req, CorrelationId corr) {
        requireManagePermission(actor);
        Category existing = menuRepo.findCategoryScoped(categoryId, actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        Category saved = menuRepo.saveCategory(new Category(
                existing.id(), existing.tenantId(), req.parentId(), req.code().trim(), req.name().trim(),
                req.displayOrder(), req.colorHex(), req.active(), existing.version()
        ));
        audit(actor, AuditAction.MENU_UPDATED, "Category", saved.id().toString(), null, corr);
        publishIfBranchActive(actor, "MENU_UPDATED", saved.id());
        return new CategoryDto(saved.id(), saved.parentId(), saved.code(), saved.name(), saved.displayOrder(), saved.colorHex(), saved.active(), true, saved.version());
    }

    public void setBranchCategoryVisibility(
            AccessPrincipal actor,
            UUID categoryId,
            BranchCategoryVisibilityRequest req,
            CorrelationId corr
    ) {
        requireManagePermission(actor);
        BranchId branchId = requireActiveBranch(actor);
        menuRepo.findCategoryScoped(categoryId, actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        menuRepo.upsertBranchCategoryVisibility(actor.tenantId(), branchId, categoryId, req.visible(), req.displayOrderOverride());
        audit(actor, AuditAction.AVAILABILITY_CHANGED, "Category", categoryId.toString(),
                "{\"visible\":" + req.visible() + "}", corr);
        eventPublisher.publish(actor.tenantId(), branchId, "MENU_UPDATED", categoryId);
    }

    public List<ModifierGroupDto> listModifierGroups(AccessPrincipal actor) {
        requirePermission(actor, PermissionCode.MENU_VIEW);
        return menuRepo.findModifierGroupsByTenant(actor.tenantId()).stream()
                .map(MenuManagementService::toModifierGroupDto)
                .toList();
    }

    public ModifierGroupDto createModifierGroup(AccessPrincipal actor, SaveModifierGroupRequest req, CorrelationId corr) {
        requireManagePermission(actor);
        if (req.maxSelections() < req.minSelections()) {
            throw new DomainException("INVALID_MODIFIER_BOUNDS", "maxSelections must be >= minSelections");
        }
        ModifierGroup saved = menuRepo.saveModifierGroup(new ModifierGroup(
                UUID.randomUUID(), actor.tenantId(), req.code().trim(), req.name().trim(),
                req.minSelections(), req.maxSelections(), req.required(), req.active(), List.of(), 0L
        ));
        audit(actor, AuditAction.MENU_CREATED, "ModifierGroup", saved.id().toString(), null, corr);
        return toModifierGroupDto(saved);
    }

    public ModifierDto createModifier(AccessPrincipal actor, UUID groupId, SaveModifierRequest req, CorrelationId corr) {
        requireManagePermission(actor);
        menuRepo.findModifierGroupScoped(groupId, actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Modifier group not found"));
        Modifier saved = menuRepo.saveModifier(new Modifier(
                UUID.randomUUID(), actor.tenantId(), groupId, req.code().trim(), req.name().trim(),
                req.priceDelta(), req.displayOrder(), req.defaultSelected(), req.active(), 0L
        ));
        audit(actor, AuditAction.MENU_CREATED, "Modifier", saved.id().toString(), null, corr);
        return toModifierDto(saved);
    }

    public List<ProductDto> searchBranchProducts(
            AccessPrincipal actor,
            UUID categoryId,
            String query,
            int page,
            int size
    ) {
        requirePermission(actor, PermissionCode.MENU_VIEW);
        return menuRepo.searchBranchProducts(actor.tenantId(), requireActiveBranch(actor), categoryId, query, page, size);
    }

    public ProductDto getProductDetail(AccessPrincipal actor, UUID productId) {
        requirePermission(actor, PermissionCode.MENU_VIEW);
        return menuRepo.findBranchProductDetail(actor.tenantId(), requireActiveBranch(actor), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    public ProductDto findByBarcode(AccessPrincipal actor, String barcode) {
        requirePermission(actor, PermissionCode.MENU_VIEW);
        if (barcode == null || barcode.isBlank()) {
            throw new DomainException("INVALID_BARCODE", "Barcode must not be blank");
        }
        return menuRepo.findBranchProductByBarcode(actor.tenantId(), requireActiveBranch(actor), barcode.trim())
                .orElseThrow(() -> new ResourceNotFoundException("No product or variant matching barcode"));
    }

    public ProductDto createProduct(AccessPrincipal actor, SaveProductRequest req, CorrelationId corr) {
        requireManagePermission(actor);
        menuRepo.findCategoryScoped(req.categoryId(), actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (req.taxId() != null) {
            menuRepo.findTaxScoped(req.taxId(), actor.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Tax not found"));
        }
        Product saved = menuRepo.saveProduct(new Product(
                UUID.randomUUID(), actor.tenantId(), req.categoryId(), req.taxId(),
                req.sku().trim(), trimOrNull(req.barcode()), req.name().trim(), trimOrNull(req.description()),
                req.basePrice(), req.pricingMode(), req.productType(), trimOrNull(req.imageRef()),
                req.displayOrder(), req.active(), req.modifierGroupIds(), 0L
        ));
        audit(actor, AuditAction.MENU_CREATED, "Product", saved.id().toString(),
                "{\"sku\":\"" + saved.sku() + "\"}", corr);
        publishIfBranchActive(actor, "MENU_UPDATED", saved.id());
        return menuRepo.findBranchProductDetail(actor.tenantId(), requireActiveBranch(actor), saved.id())
                .orElseThrow();
    }

    public ProductDto updateProduct(AccessPrincipal actor, UUID productId, SaveProductRequest req, CorrelationId corr) {
        requireManagePermission(actor);
        Product existing = menuRepo.findProductScoped(productId, actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        menuRepo.findCategoryScoped(req.categoryId(), actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (req.taxId() != null) {
            menuRepo.findTaxScoped(req.taxId(), actor.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Tax not found"));
        }
        boolean priceChanged = existing.basePrice().compareTo(req.basePrice()) != 0;
        Product saved = menuRepo.saveProduct(new Product(
                existing.id(), existing.tenantId(), req.categoryId(), req.taxId(),
                req.sku().trim(), trimOrNull(req.barcode()), req.name().trim(), trimOrNull(req.description()),
                req.basePrice(), req.pricingMode(), req.productType(), trimOrNull(req.imageRef()),
                req.displayOrder(), req.active(), req.modifierGroupIds(), existing.version()
        ));
        audit(actor, priceChanged ? AuditAction.PRICE_CHANGED : AuditAction.MENU_UPDATED,
                "Product", saved.id().toString(), null, corr);
        publishIfBranchActive(actor, "MENU_UPDATED", saved.id());
        return menuRepo.findBranchProductDetail(actor.tenantId(), requireActiveBranch(actor), saved.id())
                .orElseThrow();
    }

    public ProductVariantDto createVariant(
            AccessPrincipal actor,
            UUID productId,
            SaveProductVariantRequest req,
            CorrelationId corr
    ) {
        requireManagePermission(actor);
        menuRepo.findProductScoped(productId, actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        ProductVariant saved = menuRepo.saveVariant(new ProductVariant(
                UUID.randomUUID(), actor.tenantId(), productId, req.sku().trim(),
                trimOrNull(req.barcode()), req.name().trim(), req.priceOverride(),
                req.displayOrder(), req.active(), 0L
        ));
        audit(actor, AuditAction.MENU_CREATED, "ProductVariant", saved.id().toString(), null, corr);
        publishIfBranchActive(actor, "MENU_UPDATED", productId);
        return new ProductVariantDto(
                saved.id(), saved.productId(), saved.sku(), saved.barcode(),
                saved.name(), saved.priceOverride(), saved.displayOrder(), saved.active(), saved.version()
        );
    }

    public void setBranchProductOverride(
            AccessPrincipal actor,
            UUID productId,
            BranchProductOverrideRequest req,
            CorrelationId corr
    ) {
        requireManagePermission(actor);
        BranchId branchId = requireActiveBranch(actor);
        menuRepo.findProductScoped(productId, actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (req.taxOverrideId() != null) {
            menuRepo.findTaxScoped(req.taxOverrideId(), actor.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Tax not found"));
        }
        BranchProductOverride existing = menuRepo.findBranchProductOverride(actor.tenantId(), branchId, productId).orElse(null);
        menuRepo.upsertBranchProductOverride(new BranchProductOverride(
                existing != null ? existing.id() : UUID.randomUUID(),
                actor.tenantId(),
                branchId,
                productId,
                req.priceOverride(),
                req.taxOverrideId(),
                req.available(),
                existing != null ? existing.version() : 0L
        ));
        audit(actor, AuditAction.AVAILABILITY_CHANGED, "BranchProductOverride", productId.toString(),
                "{\"available\":" + req.available() + "}", corr);
        eventPublisher.publish(actor.tenantId(), branchId, "PRODUCT_AVAILABILITY_CHANGED", productId);
    }

    private void requirePermission(AccessPrincipal actor, PermissionCode permission) {
        BranchId branchId = requireActiveBranch(actor);
        PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), branchId, permission);
    }

    private void requireManagePermission(AccessPrincipal actor) {
        BranchId branchId = requireActiveBranch(actor);
        if (actor == null || !actor.active() || !actor.canAccessBranch(branchId)) {
            throw new UnauthorizedOperationException(PermissionCode.MENU_MANAGE, "Unauthorized branch access");
        }
        if (!actor.hasPermission(PermissionCode.MENU_MANAGE)
                && !actor.hasPermission(PermissionCode.MENU_CREATE)
                && !actor.hasPermission(PermissionCode.MENU_UPDATE)) {
            throw new UnauthorizedOperationException(PermissionCode.MENU_MANAGE, "Missing menu.manage permission");
        }
    }

    private static BranchId requireActiveBranch(AccessPrincipal actor) {
        if (actor == null || actor.activeBranchId() == null) {
            throw new UnauthorizedOperationException(PermissionCode.MENU_VIEW, "Active branch session is required");
        }
        return actor.activeBranchId();
    }

    private void audit(AccessPrincipal actor, AuditAction action, String type, String id, String json, CorrelationId corr) {
        auditService.record(actor.tenantId(), actor.activeBranchId(), actor.userId(), action, type, id, json, corr);
    }

    private void publishIfBranchActive(AccessPrincipal actor, String eventType, UUID entityId) {
        if (actor.activeBranchId() != null) {
            eventPublisher.publish(actor.tenantId(), actor.activeBranchId(), eventType, entityId);
        }
    }

    private static String trimOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ModifierGroupDto toModifierGroupDto(ModifierGroup group) {
        return new ModifierGroupDto(
                group.id(),
                group.code(),
                group.name(),
                group.minSelections(),
                group.maxSelections(),
                group.required(),
                group.active(),
                group.modifiers().stream().map(MenuManagementService::toModifierDto).toList(),
                group.version()
        );
    }

    private static ModifierDto toModifierDto(Modifier m) {
        return new ModifierDto(
                m.id(), m.modifierGroupId(), m.code(), m.name(), m.priceDelta(),
                m.displayOrder(), m.defaultSelected(), m.active(), m.version()
        );
    }
}
