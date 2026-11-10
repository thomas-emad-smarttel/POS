package com.restaurantpos.application.pos;

import com.restaurantpos.application.audit.AuditRecordingService;
import com.restaurantpos.application.security.PermissionPolicy;
import com.restaurantpos.domain.exception.ConcurrentModificationDomainException;
import com.restaurantpos.domain.exception.DomainException;
import com.restaurantpos.domain.exception.ResourceNotFoundException;
import com.restaurantpos.domain.exception.UnauthorizedOperationException;
import com.restaurantpos.domain.menu.BranchProductOverride;
import com.restaurantpos.domain.menu.ModifierGroup;
import com.restaurantpos.domain.menu.ModifierSelectionValidator;
import com.restaurantpos.domain.menu.Product;
import com.restaurantpos.domain.menu.ProductVariant;
import com.restaurantpos.domain.menu.TaxDefinition;
import com.restaurantpos.domain.order.OrderAggregate;
import com.restaurantpos.domain.order.OrderDiscountSnapshot;
import com.restaurantpos.domain.order.OrderItemDetail;
import com.restaurantpos.domain.order.OrderItemModifierSnapshot;
import com.restaurantpos.domain.order.OrderStatePolicy;
import com.restaurantpos.domain.order.OrderStatus;
import com.restaurantpos.domain.order.OrderTaxSnapshot;
import com.restaurantpos.domain.order.OrderType;
import com.restaurantpos.domain.order.PricingCalculator;
import com.restaurantpos.domain.port.BranchEventPublishPort;
import com.restaurantpos.domain.port.BranchRepositoryPort;
import com.restaurantpos.domain.port.KitchenDispatchPort;
import com.restaurantpos.domain.port.MenuRepositoryPort;
import com.restaurantpos.domain.port.PosOrderRepositoryPort;
import com.restaurantpos.domain.port.RoleRepositoryPort;
import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.domain.port.TableRepositoryPort;
import com.restaurantpos.domain.port.UserRepositoryPort;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.domain.security.RoleDefinition;
import com.restaurantpos.domain.security.UserRoleAssignment;
import com.restaurantpos.domain.table.DiningTable;
import com.restaurantpos.domain.table.TableStatePolicy;
import com.restaurantpos.domain.table.TableStatus;
import com.restaurantpos.domain.tenancy.Branch;
import com.restaurantpos.shared.dto.pos.AddOrderItemRequest;
import com.restaurantpos.shared.dto.pos.ApplyDiscountRequest;
import com.restaurantpos.shared.dto.pos.CancelOrderRequest;
import com.restaurantpos.shared.dto.pos.ChangeOrderTypeRequest;
import com.restaurantpos.shared.dto.pos.CreateOrderRequest;
import com.restaurantpos.shared.dto.pos.MergeOrderRequest;
import com.restaurantpos.shared.dto.pos.OrderDiscountDto;
import com.restaurantpos.shared.dto.pos.OrderDto;
import com.restaurantpos.shared.dto.pos.OrderItemDto;
import com.restaurantpos.shared.dto.pos.OrderItemModifierDto;
import com.restaurantpos.shared.dto.pos.OrderSummaryDto;
import com.restaurantpos.shared.dto.pos.OrderTaxDto;
import com.restaurantpos.shared.dto.pos.SplitOrderRequest;
import com.restaurantpos.shared.dto.pos.TransferOrderRequest;
import com.restaurantpos.shared.dto.pos.UpdateOrderItemRequest;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.shared.security.AuditAction;
import com.restaurantpos.shared.security.PermissionCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core POS order workflow engine enforcing server-side pricing, modifier validation,
 * discount permission caps, optimistic locking, and concurrency-safe table assignments.
 */
public class OrderManagementService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PosOrderRepositoryPort orderRepo;
    private final MenuRepositoryPort menuRepo;
    private final TableRepositoryPort tableRepo;
    private final BranchRepositoryPort branchRepo;
    private final UserRepositoryPort userRepo;
    private final RoleRepositoryPort roleRepo;
    private final KitchenDispatchPort kitchenDispatch;
    private final BranchEventPublishPort eventPublisher;
    private final AuditRecordingService auditService;
    private final SystemClockPort clock;

    public OrderManagementService(
            PosOrderRepositoryPort orderRepo,
            MenuRepositoryPort menuRepo,
            TableRepositoryPort tableRepo,
            BranchRepositoryPort branchRepo,
            UserRepositoryPort userRepo,
            RoleRepositoryPort roleRepo,
            KitchenDispatchPort kitchenDispatch,
            BranchEventPublishPort eventPublisher,
            AuditRecordingService auditService,
            SystemClockPort clock
    ) {
        this.orderRepo = Objects.requireNonNull(orderRepo);
        this.menuRepo = Objects.requireNonNull(menuRepo);
        this.tableRepo = Objects.requireNonNull(tableRepo);
        this.branchRepo = Objects.requireNonNull(branchRepo);
        this.userRepo = Objects.requireNonNull(userRepo);
        this.roleRepo = Objects.requireNonNull(roleRepo);
        this.kitchenDispatch = Objects.requireNonNull(kitchenDispatch);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.auditService = Objects.requireNonNull(auditService);
        this.clock = Objects.requireNonNull(clock);
    }

    public OrderDto createOrder(AccessPrincipal actor, CreateOrderRequest req, CorrelationId corr) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.ORDERS_CREATE);
        Branch branch = branchRepo.findByIdAndTenant(branchId, actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        OrderType orderType = OrderType.valueOf(req.orderType());
        if (orderType == OrderType.DINE_IN && req.diningTableId() == null) {
            throw new DomainException("TABLE_REQUIRED", "Dine-in orders require a dining table");
        }

        UUID orderId = UUID.randomUUID();
        if (req.diningTableId() != null) {
            claimTable(actor.tenantId(), branchId, req.diningTableId(), req.expectedTableVersion(), orderId);
        }

        Instant now = clock.now();
        String orderNumber = "ORD-" + now.toEpochMilli() + "-" + orderId.toString().substring(0, 4).toUpperCase();
        OrderAggregate aggregate = new OrderAggregate(
                orderId,
                actor.tenantId(),
                branchId,
                actor.terminalId(),
                req.diningTableId(),
                actor.userId(),
                req.assignedWaiterId() != null ? req.assignedWaiterId() : actor.userId(),
                orderNumber,
                orderType,
                OrderStatus.OPEN,
                branch.currencyCode(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                req.guestCount(),
                req.customerReference(),
                req.notes(),
                false,
                now,
                null,
                null,
                0L,
                List.of(),
                List.of(),
                List.of()
        );
        OrderAggregate saved = orderRepo.save(aggregate);
        orderRepo.recordStatusHistory(UUID.randomUUID(), actor.tenantId(), branchId, saved.id(),
                null, OrderStatus.OPEN, actor.userId(), "Order created", now);

        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.ORDER_CREATED,
                "Order", saved.id().toString(), "{\"orderNumber\":\"" + orderNumber + "\"}", corr);
        eventPublisher.publish(actor.tenantId(), branchId, "ORDER_CREATED", saved.id());
        if (req.diningTableId() != null) {
            eventPublisher.publish(actor.tenantId(), branchId, "TABLE_OCCUPIED", req.diningTableId());
        }
        return toDto(saved);
    }

    public OrderDto getOrder(AccessPrincipal actor, UUID orderId) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.ORDERS_VIEW);
        return orderRepo.findByIdScoped(orderId, actor.tenantId(), branchId)
                .map(OrderManagementService::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found in branch"));
    }

    public List<OrderSummaryDto> searchOrders(
            AccessPrincipal actor,
            OrderStatus statusFilter,
            Boolean heldFilter,
            int page,
            int size
    ) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.ORDERS_VIEW);
        return orderRepo.searchBranchOrders(actor.tenantId(), branchId, statusFilter, heldFilter, page, size);
    }

    public OrderDto addItem(AccessPrincipal actor, UUID orderId, AddOrderItemRequest req, CorrelationId corr) {
        BranchId branchId = requireEditPermission(actor);
        OrderAggregate order = loadOrderWithVersionCheck(orderId, actor.tenantId(), branchId, req.expectedOrderVersion());
        OrderStatePolicy.requireMutableForItems(order.status());

        Product product = menuRepo.findProductScoped(req.productId(), actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (!product.active()) {
            throw new DomainException("PRODUCT_INACTIVE", "Product is inactive");
        }
        BranchProductOverride override = menuRepo.findBranchProductOverride(actor.tenantId(), branchId, product.id()).orElse(null);
        if (override != null && !override.available()) {
            throw new DomainException("PRODUCT_UNAVAILABLE", "Product is unavailable in this branch");
        }

        String skuSnapshot = product.sku();
        String variantNameSnapshot = null;
        BigDecimal unitBasePrice = override != null && override.priceOverride() != null
                ? override.priceOverride()
                : product.basePrice();

        if (req.productVariantId() != null) {
            ProductVariant variant = menuRepo.findVariantScoped(req.productVariantId(), product.id(), actor.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));
            if (!variant.active()) {
                throw new DomainException("VARIANT_INACTIVE", "Product variant is inactive");
            }
            skuSnapshot = variant.sku();
            variantNameSnapshot = variant.name();
            unitBasePrice = variant.priceOverride();
        }

        List<ModifierGroup> groups = menuRepo.findModifierGroupsForProduct(product.id(), actor.tenantId());
        List<ModifierSelectionValidator.Selection> selections = req.modifiers() == null
                ? List.of()
                : req.modifiers().stream()
                .map(m -> new ModifierSelectionValidator.Selection(m.modifierId(), m.quantity()))
                .toList();
        List<ModifierSelectionValidator.SelectedResolvedModifier> resolvedMods =
                ModifierSelectionValidator.validateAndResolve(groups, selections);

        List<OrderItemModifierSnapshot> modSnapshots = resolvedMods.stream()
                .map(rm -> new OrderItemModifierSnapshot(
                        UUID.randomUUID(),
                        rm.modifier().id(),
                        rm.modifier().name(),
                        rm.modifier().priceDelta(),
                        rm.quantity()
                ))
                .toList();

        UUID effectiveTaxId = override != null && override.taxOverrideId() != null ? override.taxOverrideId() : product.taxId();
        TaxDefinition tax = effectiveTaxId != null
                ? menuRepo.findTaxScoped(effectiveTaxId, actor.tenantId()).orElse(null)
                : null;

        OrderItemDetail newItem = new OrderItemDetail(
                UUID.randomUUID(),
                product.id(),
                req.productVariantId(),
                tax != null ? tax.id() : null,
                tax != null ? tax.name() : null,
                tax != null && tax.inclusive(),
                skuSnapshot,
                product.name(),
                variantNameSnapshot,
                req.quantity(),
                unitBasePrice,
                tax != null ? tax.ratePercent() : BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                req.kitchenNotes(),
                false,
                false,
                modSnapshots,
                0L
        );

        List<OrderItemDetail> nextItems = new ArrayList<>(order.items());
        nextItems.add(newItem);

        OrderAggregate saved = recalculateAndSave(order, nextItems, order.discounts(), order.status(), order.held());
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.ORDER_ITEM_ADDED,
                "Order", saved.id().toString(), "{\"sku\":\"" + skuSnapshot + "\"}", corr);
        eventPublisher.publish(actor.tenantId(), branchId, "ORDER_UPDATED", saved.id());
        return toDto(saved);
    }

    public OrderDto updateItem(
            AccessPrincipal actor,
            UUID orderId,
            UUID itemId,
            UpdateOrderItemRequest req,
            CorrelationId corr
    ) {
        BranchId branchId = requireEditPermission(actor);
        OrderAggregate order = loadOrderWithVersionCheck(orderId, actor.tenantId(), branchId, req.expectedOrderVersion());
        OrderStatePolicy.requireMutableForItems(order.status());

        OrderItemDetail target = order.items().stream()
                .filter(i -> i.id().equals(itemId) && !i.voided())
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found"));

        if (target.sentToKitchen()) {
            throw new DomainException("ITEM_ALREADY_SENT_TO_KITCHEN",
                    "Cannot modify item modifiers or quantity after sending to kitchen");
        }

        List<OrderItemModifierSnapshot> modSnapshots = target.modifiers();
        if (req.modifiers() != null && target.productId() != null) {
            List<ModifierGroup> groups = menuRepo.findModifierGroupsForProduct(target.productId(), actor.tenantId());
            List<ModifierSelectionValidator.Selection> selections = req.modifiers().stream()
                    .map(m -> new ModifierSelectionValidator.Selection(m.modifierId(), m.quantity()))
                    .toList();
            modSnapshots = ModifierSelectionValidator.validateAndResolve(groups, selections).stream()
                    .map(rm -> new OrderItemModifierSnapshot(
                            UUID.randomUUID(),
                            rm.modifier().id(),
                            rm.modifier().name(),
                            rm.modifier().priceDelta(),
                            rm.quantity()
                    ))
                    .toList();
        }

        OrderItemDetail updatedItem = new OrderItemDetail(
                target.id(),
                target.productId(),
                target.productVariantId(),
                target.taxId(),
                target.taxNameSnapshot(),
                target.taxInclusive(),
                target.skuSnapshot(),
                target.productNameSnapshot(),
                target.variantNameSnapshot(),
                req.quantity(),
                target.unitPriceSnapshot(),
                target.taxRateSnapshot(),
                target.taxAmount(),
                target.discountAmount(),
                target.lineTotal(),
                req.kitchenNotes(),
                target.sentToKitchen(),
                target.voided(),
                modSnapshots,
                target.version()
        );

        List<OrderItemDetail> nextItems = order.items().stream()
                .map(i -> i.id().equals(itemId) ? updatedItem : i)
                .toList();

        OrderAggregate saved = recalculateAndSave(order, nextItems, order.discounts(), order.status(), order.held());
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.ORDER_MODIFIED,
                "Order", saved.id().toString(), "{\"updatedItemId\":\"" + itemId + "\"}", corr);
        eventPublisher.publish(actor.tenantId(), branchId, "ORDER_UPDATED", saved.id());
        return toDto(saved);
    }

    public OrderDto removeItem(
            AccessPrincipal actor,
            UUID orderId,
            UUID itemId,
            long expectedOrderVersion,
            CorrelationId corr
    ) {
        BranchId branchId = requireEditPermission(actor);
        OrderAggregate order = loadOrderWithVersionCheck(orderId, actor.tenantId(), branchId, expectedOrderVersion);
        OrderStatePolicy.requireMutableForItems(order.status());

        OrderItemDetail target = order.items().stream()
                .filter(i -> i.id().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found"));

        if (target.sentToKitchen()) {
            throw new DomainException("ITEM_ALREADY_SENT_TO_KITCHEN",
                    "Cannot remove an item already sent to kitchen");
        }

        List<OrderItemDetail> nextItems = order.items().stream()
                .filter(i -> !i.id().equals(itemId))
                .toList();
        List<OrderDiscountSnapshot> nextDiscounts = order.discounts().stream()
                .filter(d -> d.orderItemId() == null || !d.orderItemId().equals(itemId))
                .toList();

        OrderAggregate saved = recalculateAndSave(order, nextItems, nextDiscounts, order.status(), order.held());
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.ORDER_ITEM_REMOVED,
                "Order", saved.id().toString(), "{\"removedItemId\":\"" + itemId + "\"}", corr);
        eventPublisher.publish(actor.tenantId(), branchId, "ORDER_UPDATED", saved.id());
        return toDto(saved);
    }

    public OrderDto applyDiscount(AccessPrincipal actor, UUID orderId, ApplyDiscountRequest req, CorrelationId corr) {
        BranchId branchId = requireDiscountPermission(actor);
        OrderAggregate order = loadOrderWithVersionCheck(orderId, actor.tenantId(), branchId, req.expectedOrderVersion());
        OrderStatePolicy.requireMutableForItems(order.status());

        BigDecimal baseAmount;
        if (req.orderItemId() != null) {
            OrderItemDetail item = order.items().stream()
                    .filter(i -> i.id().equals(req.orderItemId()) && !i.voided())
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Target order item not found"));
            baseAmount = computeGrossLine(item);
        } else {
            baseAmount = order.subtotalAmount();
        }

        if (baseAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("EMPTY_ORDER_DISCOUNT", "Cannot apply discount to an empty order");
        }

        BigDecimal appliedAmount;
        BigDecimal effectivePct;
        if ("PERCENTAGE".equals(req.discountType())) {
            if (req.discountValue().compareTo(HUNDRED) > 0) {
                throw new DomainException("INVALID_DISCOUNT_PCT", "Percentage discount cannot exceed 100%");
            }
            effectivePct = req.discountValue();
            appliedAmount = baseAmount.multiply(req.discountValue()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        } else {
            appliedAmount = req.discountValue().min(baseAmount).setScale(2, RoundingMode.HALF_UP);
            effectivePct = appliedAmount.multiply(HUNDRED).divide(baseAmount, 2, RoundingMode.HALF_UP);
        }

        BigDecimal roleCapPct = resolveActorMaxDiscountPct(actor, branchId);
        if (effectivePct.compareTo(roleCapPct) > 0 && !actor.hasPermission(PermissionCode.ORDERS_DISCOUNT_APPROVE)) {
            if (req.supervisorApprovalId() == null) {
                throw new UnauthorizedOperationException(
                        PermissionCode.ORDERS_DISCOUNT_APPROVE,
                        "Discount " + effectivePct + "% exceeds role limit of " + roleCapPct + "%"
                );
            }
        }

        OrderDiscountSnapshot discount = new OrderDiscountSnapshot(
                UUID.randomUUID(),
                req.orderItemId(),
                req.discountCode(),
                req.discountName().trim(),
                req.discountType(),
                req.discountValue(),
                appliedAmount,
                actor.userId(),
                req.supervisorApprovalId() != null ? actor.userId() : null,
                req.reason()
        );

        List<OrderDiscountSnapshot> nextDiscounts = new ArrayList<>(order.discounts());
        nextDiscounts.add(discount);

        OrderAggregate saved = recalculateAndSave(order, order.items(), nextDiscounts, order.status(), order.held());
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.DISCOUNT_APPLIED,
                "Order", saved.id().toString(), "{\"appliedAmount\":" + appliedAmount + "}", corr);
        eventPublisher.publish(actor.tenantId(), branchId, "ORDER_UPDATED", saved.id());
        return toDto(saved);
    }

    public OrderDto holdOrder(AccessPrincipal actor, UUID orderId, long expectedOrderVersion, CorrelationId corr) {
        BranchId branchId = requireEditPermission(actor);
        OrderAggregate order = loadOrderWithVersionCheck(orderId, actor.tenantId(), branchId, expectedOrderVersion);
        OrderStatePolicy.requireTransition(order.status(), OrderStatus.ON_HOLD);

        OrderAggregate saved = recalculateAndSave(order, order.items(), order.discounts(), OrderStatus.ON_HOLD, true);
        orderRepo.recordStatusHistory(UUID.randomUUID(), actor.tenantId(), branchId, saved.id(),
                order.status(), OrderStatus.ON_HOLD, actor.userId(), "Order placed on hold", clock.now());
        eventPublisher.publish(actor.tenantId(), branchId, "ORDER_UPDATED", saved.id());
        return toDto(saved);
    }

    public OrderDto retrieveHeldOrder(AccessPrincipal actor, UUID orderId, long expectedOrderVersion, CorrelationId corr) {
        BranchId branchId = requireEditPermission(actor);
        OrderAggregate order = loadOrderWithVersionCheck(orderId, actor.tenantId(), branchId, expectedOrderVersion);
        if (order.status() != OrderStatus.ON_HOLD) {
            throw new DomainException("ORDER_NOT_ON_HOLD", "Order is not currently on hold");
        }
        OrderAggregate saved = recalculateAndSave(order, order.items(), order.discounts(), OrderStatus.OPEN, false);
        orderRepo.recordStatusHistory(UUID.randomUUID(), actor.tenantId(), branchId, saved.id(),
                OrderStatus.ON_HOLD, OrderStatus.OPEN, actor.userId(), "Held order retrieved", clock.now());
        eventPublisher.publish(actor.tenantId(), branchId, "ORDER_UPDATED", saved.id());
        return toDto(saved);
    }

    public OrderDto changeOrderType(AccessPrincipal actor, UUID orderId, ChangeOrderTypeRequest req, CorrelationId corr) {
        BranchId branchId = requireEditPermission(actor);
        OrderAggregate order = loadOrderWithVersionCheck(orderId, actor.tenantId(), branchId, req.expectedOrderVersion());
        OrderStatePolicy.requireMutableForItems(order.status());

        OrderType targetType = OrderType.valueOf(req.orderType());
        if (targetType == OrderType.DINE_IN && req.diningTableId() == null) {
            throw new DomainException("TABLE_REQUIRED", "Dine-in orders require a dining table");
        }

        if (order.diningTableId() != null && !order.diningTableId().equals(req.diningTableId())) {
            releaseTable(actor.tenantId(), branchId, order.diningTableId(), order.id());
        }
        if (req.diningTableId() != null && !req.diningTableId().equals(order.diningTableId())) {
            claimTable(actor.tenantId(), branchId, req.diningTableId(), null, order.id());
        }

        OrderAggregate updated = new OrderAggregate(
                order.id(), order.tenantId(), order.branchId(), order.terminalId(), req.diningTableId(),
                order.createdByUserId(), order.assignedWaiterId(), order.orderNumber(), targetType,
                order.status(), order.currencyCode(), order.subtotalAmount(), order.discountAmount(),
                order.taxAmount(), order.totalAmount(), order.paidAmount(), order.guestCount(),
                req.customerReference(), order.notes(), order.held(), order.openedAt(),
                order.sentToKitchenAt(), order.closedAt(), order.version(),
                order.items(), order.discounts(), order.taxes()
        );
        OrderAggregate saved = orderRepo.save(updated);
        eventPublisher.publish(actor.tenantId(), branchId, "ORDER_UPDATED", saved.id());
        return toDto(saved);
    }

    public OrderDto transferOrder(AccessPrincipal actor, UUID orderId, TransferOrderRequest req, CorrelationId corr) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.ORDERS_TRANSFER);
        OrderAggregate order = loadOrderWithVersionCheck(orderId, actor.tenantId(), branchId, req.expectedOrderVersion());
        OrderStatePolicy.requireMutableForItems(order.status());

        UUID sourceTableId = order.diningTableId();
        if (req.targetTableId().equals(sourceTableId)) {
            throw new DomainException("SAME_TABLE_TRANSFER", "Source and target tables are identical");
        }

        claimTable(actor.tenantId(), branchId, req.targetTableId(), req.expectedTargetTableVersion(), order.id());
        if (sourceTableId != null) {
            releaseTable(actor.tenantId(), branchId, sourceTableId, order.id());
        }

        OrderAggregate updated = new OrderAggregate(
                order.id(), order.tenantId(), order.branchId(), order.terminalId(), req.targetTableId(),
                order.createdByUserId(), order.assignedWaiterId(), order.orderNumber(), OrderType.DINE_IN,
                order.status(), order.currencyCode(), order.subtotalAmount(), order.discountAmount(),
                order.taxAmount(), order.totalAmount(), order.paidAmount(), order.guestCount(),
                order.customerReference(), order.notes(), order.held(), order.openedAt(),
                order.sentToKitchenAt(), order.closedAt(), order.version(),
                order.items(), order.discounts(), order.taxes()
        );
        OrderAggregate saved = orderRepo.save(updated);

        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.ORDER_TRANSFERRED,
                "Order", saved.id().toString(), "{\"targetTableId\":\"" + req.targetTableId() + "\"}", corr);
        eventPublisher.publish(actor.tenantId(), branchId, "ORDER_TRANSFERRED", saved.id());
        if (sourceTableId != null) {
            eventPublisher.publish(actor.tenantId(), branchId, "TABLE_RELEASED", sourceTableId);
        }
        eventPublisher.publish(actor.tenantId(), branchId, "TABLE_OCCUPIED", req.targetTableId());
        return toDto(saved);
    }

    public List<OrderDto> splitOrder(AccessPrincipal actor, UUID orderId, SplitOrderRequest req, CorrelationId corr) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.ORDERS_SPLIT);
        OrderAggregate source = loadOrderWithVersionCheck(orderId, actor.tenantId(), branchId, req.expectedOrderVersion());
        OrderStatePolicy.requireMutableForItems(source.status());

        Set<UUID> moveIds = Set.copyOf(req.orderItemIds());
        List<OrderItemDetail> movingItems = source.items().stream()
                .filter(i -> moveIds.contains(i.id()))
                .toList();
        if (movingItems.isEmpty() || movingItems.size() == source.items().size()) {
            throw new DomainException("INVALID_SPLIT_SELECTION", "Select at least one and fewer than all items to split");
        }
        List<OrderItemDetail> remainingItems = source.items().stream()
                .filter(i -> !moveIds.contains(i.id()))
                .toList();

        UUID newOrderId = UUID.randomUUID();
        if (req.targetTableId() != null) {
            claimTable(actor.tenantId(), branchId, req.targetTableId(), null, newOrderId);
        }

        Instant now = clock.now();
        OrderAggregate newOrderDraft = new OrderAggregate(
                newOrderId, actor.tenantId(), branchId, actor.terminalId(), req.targetTableId(),
                actor.userId(), source.assignedWaiterId(),
                source.orderNumber() + "-S" + newOrderId.toString().substring(0, 3).toUpperCase(),
                req.targetTableId() != null ? OrderType.DINE_IN : source.orderType(),
                OrderStatus.OPEN, source.currencyCode(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                1, source.customerReference(), source.notes(), false,
                now, null, null, 0L, List.of(), List.of(), List.of()
        );

        List<OrderItemDetail> clonedMovingItems = movingItems.stream()
                .map(i -> new OrderItemDetail(
                        UUID.randomUUID(), i.productId(), i.productVariantId(), i.taxId(), i.taxNameSnapshot(),
                        i.taxInclusive(), i.skuSnapshot(), i.productNameSnapshot(), i.variantNameSnapshot(),
                        i.quantity(), i.unitPriceSnapshot(), i.taxRateSnapshot(), BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, i.kitchenNotes(), i.sentToKitchen(), i.voided(),
                        i.modifiers().stream().map(m -> new OrderItemModifierSnapshot(
                                UUID.randomUUID(), m.modifierId(), m.modifierNameSnapshot(), m.unitPriceSnapshot(), m.quantity()
                        )).toList(), 0L
                ))
                .toList();

        OrderAggregate savedSource = recalculateAndSave(source, remainingItems, source.discounts(), source.status(), source.held());
        OrderAggregate savedSplit = recalculateAndSave(newOrderDraft, clonedMovingItems, List.of(), OrderStatus.OPEN, false);

        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.ORDER_SPLIT,
                "Order", savedSource.id().toString(), "{\"splitOrderId\":\"" + savedSplit.id() + "\"}", corr);
        eventPublisher.publish(actor.tenantId(), branchId, "ORDER_UPDATED", savedSource.id());
        eventPublisher.publish(actor.tenantId(), branchId, "ORDER_CREATED", savedSplit.id());
        return List.of(toDto(savedSource), toDto(savedSplit));
    }

    public OrderDto mergeOrders(AccessPrincipal actor, UUID targetOrderId, MergeOrderRequest req, CorrelationId corr) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.ORDERS_MERGE);
        if (targetOrderId.equals(req.sourceOrderId())) {
            throw new DomainException("SAME_ORDER_MERGE", "Cannot merge an order into itself");
        }
        OrderAggregate target = loadOrderWithVersionCheck(targetOrderId, actor.tenantId(), branchId, req.expectedTargetOrderVersion());
        OrderAggregate source = loadOrderWithVersionCheck(req.sourceOrderId(), actor.tenantId(), branchId, req.expectedSourceOrderVersion());
        OrderStatePolicy.requireMutableForItems(target.status());
        OrderStatePolicy.requireMutableForItems(source.status());

        List<OrderItemDetail> combinedItems = new ArrayList<>(target.items());
        for (OrderItemDetail srcItem : source.items()) {
            combinedItems.add(new OrderItemDetail(
                    UUID.randomUUID(), srcItem.productId(), srcItem.productVariantId(), srcItem.taxId(),
                    srcItem.taxNameSnapshot(), srcItem.taxInclusive(), srcItem.skuSnapshot(),
                    srcItem.productNameSnapshot(), srcItem.variantNameSnapshot(), srcItem.quantity(),
                    srcItem.unitPriceSnapshot(), srcItem.taxRateSnapshot(), BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, srcItem.kitchenNotes(), srcItem.sentToKitchen(),
                    srcItem.voided(),
                    srcItem.modifiers().stream().map(m -> new OrderItemModifierSnapshot(
                            UUID.randomUUID(), m.modifierId(), m.modifierNameSnapshot(), m.unitPriceSnapshot(), m.quantity()
                    )).toList(),
                    0L
            ));
        }

        OrderAggregate savedTarget = recalculateAndSave(target, combinedItems, target.discounts(), target.status(), target.held());

        if (source.diningTableId() != null) {
            releaseTable(actor.tenantId(), branchId, source.diningTableId(), source.id());
        }
        OrderAggregate cancelledSource = new OrderAggregate(
                source.id(), source.tenantId(), source.branchId(), source.terminalId(), null,
                source.createdByUserId(), source.assignedWaiterId(), source.orderNumber(), source.orderType(),
                OrderStatus.CANCELLED, source.currencyCode(), source.subtotalAmount(), source.discountAmount(),
                source.taxAmount(), source.totalAmount(), source.paidAmount(), source.guestCount(),
                source.customerReference(), source.notes(), false, source.openedAt(),
                source.sentToKitchenAt(), clock.now(), source.version(),
                source.items(), source.discounts(), source.taxes()
        );
        orderRepo.save(cancelledSource);
        orderRepo.recordStatusHistory(UUID.randomUUID(), actor.tenantId(), branchId, source.id(),
                source.status(), OrderStatus.CANCELLED, actor.userId(), "Merged into " + target.orderNumber(), clock.now());

        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.ORDER_MERGED,
                "Order", savedTarget.id().toString(), "{\"sourceOrderId\":\"" + source.id() + "\"}", corr);
        eventPublisher.publish(actor.tenantId(), branchId, "ORDER_UPDATED", savedTarget.id());
        eventPublisher.publish(actor.tenantId(), branchId, "ORDER_CANCELLED", source.id());
        return toDto(savedTarget);
    }

    public OrderDto sendToKitchen(AccessPrincipal actor, UUID orderId, long expectedOrderVersion, CorrelationId corr) {
        BranchId branchId = requireEditPermission(actor);
        OrderAggregate order = loadOrderWithVersionCheck(orderId, actor.tenantId(), branchId, expectedOrderVersion);
        if (order.items().isEmpty()) {
            throw new DomainException("EMPTY_ORDER", "Cannot send an empty order to kitchen");
        }
        OrderStatePolicy.requireTransition(order.status(), OrderStatus.IN_KITCHEN);

        Instant now = clock.now();
        List<OrderItemDetail> markedItems = order.items().stream()
                .map(i -> new OrderItemDetail(
                        i.id(), i.productId(), i.productVariantId(), i.taxId(), i.taxNameSnapshot(),
                        i.taxInclusive(), i.skuSnapshot(), i.productNameSnapshot(), i.variantNameSnapshot(),
                        i.quantity(), i.unitPriceSnapshot(), i.taxRateSnapshot(), i.taxAmount(),
                        i.discountAmount(), i.lineTotal(), i.kitchenNotes(), true, i.voided(),
                        i.modifiers(), i.version()
                ))
                .toList();

        OrderAggregate next = new OrderAggregate(
                order.id(), order.tenantId(), order.branchId(), order.terminalId(), order.diningTableId(),
                order.createdByUserId(), order.assignedWaiterId(), order.orderNumber(), order.orderType(),
                OrderStatus.IN_KITCHEN, order.currencyCode(), order.subtotalAmount(), order.discountAmount(),
                order.taxAmount(), order.totalAmount(), order.paidAmount(), order.guestCount(),
                order.customerReference(), order.notes(), false, order.openedAt(),
                order.sentToKitchenAt() != null ? order.sentToKitchenAt() : now,
                order.closedAt(), order.version(), markedItems, order.discounts(), order.taxes()
        );
        OrderAggregate saved = orderRepo.save(next);
        kitchenDispatch.dispatchOrderToKitchen(saved);
        orderRepo.recordStatusHistory(UUID.randomUUID(), actor.tenantId(), branchId, saved.id(),
                order.status(), OrderStatus.IN_KITCHEN, actor.userId(), "Sent to kitchen", now);

        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.ORDER_SENT_TO_KITCHEN,
                "Order", saved.id().toString(), null, corr);
        eventPublisher.publish(actor.tenantId(), branchId, "ORDER_UPDATED", saved.id());
        return toDto(saved);
    }

    public OrderDto cancelOrder(AccessPrincipal actor, UUID orderId, CancelOrderRequest req, CorrelationId corr) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.ORDERS_CANCEL);
        OrderAggregate order = loadOrderWithVersionCheck(orderId, actor.tenantId(), branchId, req.expectedOrderVersion());
        OrderStatePolicy.requireTransition(order.status(), OrderStatus.CANCELLED);

        if ((order.status() == OrderStatus.PAID || order.sentToKitchenAt() != null) && req.supervisorApprovalId() == null
                && !actor.hasPermission(PermissionCode.ORDERS_DISCOUNT_APPROVE)) {
            throw new UnauthorizedOperationException(
                    PermissionCode.ORDERS_CANCEL,
                    "Cancelling an order sent to kitchen or paid requires supervisor approval"
            );
        }

        UUID releasedTableId = order.diningTableId();
        if (releasedTableId != null) {
            releaseTable(actor.tenantId(), branchId, releasedTableId, order.id());
        }

        Instant now = clock.now();
        OrderAggregate cancelled = new OrderAggregate(
                order.id(), order.tenantId(), order.branchId(), order.terminalId(), null,
                order.createdByUserId(), order.assignedWaiterId(), order.orderNumber(), order.orderType(),
                OrderStatus.CANCELLED, order.currencyCode(), order.subtotalAmount(), order.discountAmount(),
                order.taxAmount(), order.totalAmount(), order.paidAmount(), order.guestCount(),
                order.customerReference(), order.notes(), false, order.openedAt(),
                order.sentToKitchenAt(), now, order.version(), order.items(), order.discounts(), order.taxes()
        );
        OrderAggregate saved = orderRepo.save(cancelled);
        if (order.sentToKitchenAt() != null) {
            kitchenDispatch.dispatchOrderCancelled(saved, req.reason().trim());
        }
        orderRepo.recordStatusHistory(UUID.randomUUID(), actor.tenantId(), branchId, saved.id(),
                order.status(), OrderStatus.CANCELLED, actor.userId(), req.reason().trim(), now);

        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.ORDER_CANCELLED,
                "Order", saved.id().toString(), "{\"reason\":\"" + req.reason().trim().replace("\"", "'") + "\"}", corr);
        eventPublisher.publish(actor.tenantId(), branchId, "ORDER_CANCELLED", saved.id());
        if (releasedTableId != null) {
            eventPublisher.publish(actor.tenantId(), branchId, "TABLE_RELEASED", releasedTableId);
        }
        return toDto(saved);
    }

    public OrderDto reopenOrder(AccessPrincipal actor, UUID orderId, long expectedOrderVersion, CorrelationId corr) {
        BranchId branchId = requireEditPermission(actor);
        OrderAggregate order = loadOrderWithVersionCheck(orderId, actor.tenantId(), branchId, expectedOrderVersion);
        OrderStatePolicy.requireTransition(order.status(), OrderStatus.OPEN);

        OrderAggregate reopened = new OrderAggregate(
                order.id(), order.tenantId(), order.branchId(), order.terminalId(), order.diningTableId(),
                order.createdByUserId(), order.assignedWaiterId(), order.orderNumber(), order.orderType(),
                OrderStatus.OPEN, order.currencyCode(), order.subtotalAmount(), order.discountAmount(),
                order.taxAmount(), order.totalAmount(), order.paidAmount(), order.guestCount(),
                order.customerReference(), order.notes(), false, order.openedAt(),
                order.sentToKitchenAt(), null, order.version(), order.items(), order.discounts(), order.taxes()
        );
        OrderAggregate saved = orderRepo.save(reopened);
        orderRepo.recordStatusHistory(UUID.randomUUID(), actor.tenantId(), branchId, saved.id(),
                order.status(), OrderStatus.OPEN, actor.userId(), "Order reopened", clock.now());
        eventPublisher.publish(actor.tenantId(), branchId, "ORDER_UPDATED", saved.id());
        return toDto(saved);
    }

    private OrderAggregate recalculateAndSave(
            OrderAggregate base,
            List<OrderItemDetail> items,
            List<OrderDiscountSnapshot> discounts,
            OrderStatus nextStatus,
            boolean held
    ) {
        Map<UUID, BigDecimal> itemLevelDiscounts = new HashMap<>();
        List<PricingCalculator.OrderDiscountInput> orderDiscounts = new ArrayList<>();
        for (OrderDiscountSnapshot d : discounts) {
            if (d.orderItemId() != null) {
                itemLevelDiscounts.merge(d.orderItemId(), d.appliedAmount(), BigDecimal::add);
            } else {
                orderDiscounts.add(new PricingCalculator.OrderDiscountInput(d.discountType(), d.discountValue()));
            }
        }

        List<PricingCalculator.LineInput> calcInputs = new ArrayList<>();
        for (OrderItemDetail item : items) {
            if (item.voided()) {
                continue;
            }
            BigDecimal modUnitTotal = item.modifiers().stream()
                    .map(m -> m.unitPriceSnapshot().multiply(BigDecimal.valueOf(m.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            calcInputs.add(new PricingCalculator.LineInput(
                    item.id(),
                    item.unitPriceSnapshot(),
                    modUnitTotal,
                    item.quantity(),
                    item.taxId(),
                    item.taxNameSnapshot(),
                    item.taxRateSnapshot(),
                    item.taxInclusive(),
                    itemLevelDiscounts.getOrDefault(item.id(), BigDecimal.ZERO)
            ));
        }

        PricingCalculator.OrderCalculationResult calc = PricingCalculator.calculate(
                base.currencyCode(), calcInputs, orderDiscounts
        );

        Map<UUID, PricingCalculator.ComputedLine> computedById = calc.lines().stream()
                .collect(Collectors.toMap(PricingCalculator.ComputedLine::lineId, l -> l));

        List<OrderItemDetail> recalculatedItems = items.stream()
                .map(i -> {
                    PricingCalculator.ComputedLine c = computedById.get(i.id());
                    if (c == null) {
                        return i;
                    }
                    return new OrderItemDetail(
                            i.id(), i.productId(), i.productVariantId(), i.taxId(), i.taxNameSnapshot(),
                            i.taxInclusive(), i.skuSnapshot(), i.productNameSnapshot(), i.variantNameSnapshot(),
                            i.quantity(), i.unitPriceSnapshot(), i.taxRateSnapshot(),
                            c.taxAmount(), c.itemDiscountAmount(), c.lineTotal(),
                            i.kitchenNotes(), i.sentToKitchen(), i.voided(), i.modifiers(), i.version()
                    );
                })
                .toList();

        List<OrderTaxSnapshot> taxSnapshots = calc.taxSummaries().stream()
                .map(ts -> new OrderTaxSnapshot(
                        UUID.randomUUID(), ts.taxId(), ts.taxName(), ts.ratePercent(),
                        ts.inclusive(), ts.taxableAmount(), ts.taxAmount()
                ))
                .toList();

        OrderAggregate updated = new OrderAggregate(
                base.id(), base.tenantId(), base.branchId(), base.terminalId(), base.diningTableId(),
                base.createdByUserId(), base.assignedWaiterId(), base.orderNumber(), base.orderType(),
                nextStatus, base.currencyCode(),
                calc.subtotal().amount(), calc.discountTotal().amount(), calc.taxTotal().amount(),
                calc.grandTotal().amount(), base.paidAmount(), base.guestCount(),
                base.customerReference(), base.notes(), held, base.openedAt(),
                base.sentToKitchenAt(), base.closedAt(), base.version(),
                recalculatedItems, discounts, taxSnapshots
        );
        return orderRepo.save(updated);
    }

    private void claimTable(TenantId tenantId, BranchId branchId, UUID tableId, Long expectedVersion, UUID orderId) {
        DiningTable table = tableRepo.findTableScoped(tableId, tenantId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Dining table not found in active branch"));
        if (expectedVersion != null && table.version() != expectedVersion) {
            throw new ConcurrentModificationDomainException("Dining table was modified by another terminal", null);
        }
        TableStatePolicy.requireAvailableForOrder(table);
        tableRepo.saveTable(new DiningTable(
                table.id(), table.tenantId(), table.branchId(), table.diningAreaId(), table.diningAreaName(),
                table.tableCode(), table.displayName(), table.capacity(), table.sortOrder(),
                TableStatus.OCCUPIED, orderId, table.active(), table.version()
        ));
    }

    private void releaseTable(TenantId tenantId, BranchId branchId, UUID tableId, UUID orderId) {
        tableRepo.findTableScoped(tableId, tenantId, branchId).ifPresent(table -> {
            if (orderId.equals(table.activeOrderId())) {
                tableRepo.saveTable(new DiningTable(
                        table.id(), table.tenantId(), table.branchId(), table.diningAreaId(), table.diningAreaName(),
                        table.tableCode(), table.displayName(), table.capacity(), table.sortOrder(),
                        TableStatus.CLEANING, null, table.active(), table.version()
                ));
            }
        });
    }

    private OrderAggregate loadOrderWithVersionCheck(UUID orderId, TenantId tenantId, BranchId branchId, long expectedVersion) {
        OrderAggregate order = orderRepo.findByIdScoped(orderId, tenantId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found in branch"));
        if (order.version() != expectedVersion) {
            throw new ConcurrentModificationDomainException(
                    "Order " + order.orderNumber() + " was modified by another terminal (expected v"
                            + expectedVersion + ", actual v" + order.version() + ")",
                    null
            );
        }
        return order;
    }

    private BigDecimal resolveActorMaxDiscountPct(AccessPrincipal actor, BranchId branchId) {
        List<UserRoleAssignment> assignments = userRepo.findRoleAssignments(actor.tenantId(), actor.userId());
        BigDecimal maxPct = BigDecimal.ZERO;
        for (UserRoleAssignment a : assignments) {
            if (a.branchId() == null || a.branchId().equals(branchId.value())) {
                RoleDefinition role = roleRepo.findByIdAndTenant(a.roleId(), actor.tenantId()).orElse(null);
                if (role != null && role.active() && role.maxDiscountPct().compareTo(maxPct) > 0) {
                    maxPct = role.maxDiscountPct();
                }
            }
        }
        return maxPct;
    }

    private static BigDecimal computeGrossLine(OrderItemDetail item) {
        BigDecimal modUnit = item.modifiers().stream()
                .map(m -> m.unitPriceSnapshot().multiply(BigDecimal.valueOf(m.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return item.unitPriceSnapshot().add(modUnit).multiply(item.quantity()).setScale(2, RoundingMode.HALF_UP);
    }

    private static BranchId requireBranchPermission(AccessPrincipal actor, PermissionCode permission) {
        if (actor == null || actor.activeBranchId() == null) {
            throw new UnauthorizedOperationException(permission, "Active branch session is required");
        }
        PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), permission);
        return actor.activeBranchId();
    }

    private static BranchId requireEditPermission(AccessPrincipal actor) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.ORDERS_VIEW);
        if (!actor.hasPermission(PermissionCode.ORDERS_EDIT) && !actor.hasPermission(PermissionCode.ORDERS_UPDATE)) {
            throw new UnauthorizedOperationException(PermissionCode.ORDERS_EDIT, "Missing orders.edit permission");
        }
        return branchId;
    }

    private static BranchId requireDiscountPermission(AccessPrincipal actor) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.ORDERS_VIEW);
        if (!actor.hasPermission(PermissionCode.ORDERS_DISCOUNT) && !actor.hasPermission(PermissionCode.ORDERS_DISCOUNT_APPLY)) {
            throw new UnauthorizedOperationException(PermissionCode.ORDERS_DISCOUNT, "Missing orders.discount permission");
        }
        return branchId;
    }

    public static OrderDto toDto(OrderAggregate a) {
        return new OrderDto(
                a.id(), a.tenantId().value(), a.branchId().value(), a.terminalId(), a.diningTableId(),
                a.createdByUserId(), a.assignedWaiterId(), a.orderNumber(), a.orderType().name(),
                a.status().name(), a.currencyCode(), a.subtotalAmount(), a.discountAmount(),
                a.taxAmount(), a.totalAmount(), a.paidAmount(), a.guestCount(), a.customerReference(),
                a.notes(), a.held(), a.openedAt(), a.sentToKitchenAt(), a.closedAt(), a.version(),
                a.items().stream().map(i -> new OrderItemDto(
                        i.id(), i.productId(), i.productVariantId(), i.skuSnapshot(), i.productNameSnapshot(),
                        i.variantNameSnapshot(), i.quantity(), i.unitPriceSnapshot(), i.taxRateSnapshot(),
                        i.taxAmount(), i.discountAmount(), i.lineTotal(), i.kitchenNotes(), i.sentToKitchen(),
                        i.voided(),
                        i.modifiers().stream().map(m -> new OrderItemModifierDto(
                                m.id(), m.modifierId(), m.modifierNameSnapshot(), m.unitPriceSnapshot(), m.quantity()
                        )).toList(),
                        i.version()
                )).toList(),
                a.discounts().stream().map(d -> new OrderDiscountDto(
                        d.id(), d.orderItemId(), d.discountCode(), d.discountName(), d.discountType(),
                        d.discountValue(), d.appliedAmount(), d.appliedByUserId(), d.approvedByUserId(), d.reason()
                )).toList(),
                a.taxes().stream().map(t -> new OrderTaxDto(
                        t.id(), t.taxId(), t.taxNameSnapshot(), t.ratePercentSnapshot(), t.inclusive(),
                        t.taxableAmount(), t.taxAmount()
                )).toList()
        );
    }
}
