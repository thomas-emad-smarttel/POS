package com.restaurantpos.infrastructure.persistence.adapter;

import com.restaurantpos.domain.order.OrderAggregate;
import com.restaurantpos.domain.order.OrderDiscountSnapshot;
import com.restaurantpos.domain.order.OrderItemDetail;
import com.restaurantpos.domain.order.OrderItemModifierSnapshot;
import com.restaurantpos.domain.order.OrderStatus;
import com.restaurantpos.domain.order.OrderTaxSnapshot;
import com.restaurantpos.domain.order.OrderType;
import com.restaurantpos.domain.port.PosOrderRepositoryPort;
import com.restaurantpos.infrastructure.persistence.DatabaseExceptionTranslator;
import com.restaurantpos.infrastructure.persistence.entity.OrderDiscountEntity;
import com.restaurantpos.infrastructure.persistence.entity.OrderEntity;
import com.restaurantpos.infrastructure.persistence.entity.OrderItemEntity;
import com.restaurantpos.infrastructure.persistence.entity.OrderItemModifierEntity;
import com.restaurantpos.infrastructure.persistence.entity.OrderStatusHistoryEntity;
import com.restaurantpos.infrastructure.persistence.entity.OrderTaxEntity;
import com.restaurantpos.infrastructure.persistence.repository.OrderDiscountJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.OrderItemJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.OrderItemModifierJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.OrderJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.OrderStatusHistoryJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.OrderTaxJpaRepository;
import com.restaurantpos.shared.dto.pos.OrderSummaryDto;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class PosOrderJpaAdapter implements PosOrderRepositoryPort {

    private final OrderJpaRepository orderRepo;
    private final OrderItemJpaRepository itemRepo;
    private final OrderItemModifierJpaRepository itemModRepo;
    private final OrderDiscountJpaRepository discountRepo;
    private final OrderTaxJpaRepository taxRepo;
    private final OrderStatusHistoryJpaRepository historyRepo;
    private final DatabaseExceptionTranslator translator;

    public PosOrderJpaAdapter(
            OrderJpaRepository orderRepo,
            OrderItemJpaRepository itemRepo,
            OrderItemModifierJpaRepository itemModRepo,
            OrderDiscountJpaRepository discountRepo,
            OrderTaxJpaRepository taxRepo,
            OrderStatusHistoryJpaRepository historyRepo,
            DatabaseExceptionTranslator translator
    ) {
        this.orderRepo = orderRepo;
        this.itemRepo = itemRepo;
        this.itemModRepo = itemModRepo;
        this.discountRepo = discountRepo;
        this.taxRepo = taxRepo;
        this.historyRepo = historyRepo;
        this.translator = translator;
    }

    @Override
    @Transactional
    public OrderAggregate save(OrderAggregate order) {
        return translator.execute("save pos order", () -> {
            Optional<OrderEntity> existingOpt = orderRepo.findByIdAndTenantIdAndBranchId(
                    order.id(), order.tenantId().value(), order.branchId().value());

            OrderEntity entity;
            if (existingOpt.isPresent()) {
                entity = existingOpt.get();
                if (entity.getVersion() != order.version()) {
                    throw new OptimisticLockingFailureException(
                            "Order " + order.orderNumber() + " version mismatch: expected "
                                    + order.version() + " but was " + entity.getVersion()
                    );
                }
                entity.setDiningTableId(order.diningTableId());
                entity.setAssignedWaiterId(order.assignedWaiterId());
                entity.setOrderType(order.orderType().name());
                entity.setStatus(order.status().name());
                entity.setSubtotalAmount(order.subtotalAmount());
                entity.setDiscountAmount(order.discountAmount());
                entity.setTaxAmount(order.taxAmount());
                entity.setTotalAmount(order.totalAmount());
                entity.setPaidAmount(order.paidAmount());
                entity.setGuestCount(order.guestCount());
                entity.setCustomerReference(order.customerReference());
                entity.setNotes(order.notes());
                entity.setHeld(order.held());
                entity.setSentToKitchenAt(order.sentToKitchenAt());
                entity.setClosedAt(order.closedAt());
            } else {
                entity = new OrderEntity(
                        order.id(), order.tenantId().value(), order.branchId().value(),
                        order.createdByUserId(), order.orderNumber(), order.orderType().name(),
                        order.status().name(), order.currencyCode(), order.subtotalAmount(),
                        order.discountAmount(), order.taxAmount(), order.totalAmount(),
                        order.paidAmount(), order.guestCount(), order.openedAt()
                );
                entity.setTerminalId(order.terminalId());
                entity.setDiningTableId(order.diningTableId());
                entity.setAssignedWaiterId(order.assignedWaiterId());
                entity.setCustomerReference(order.customerReference());
                entity.setNotes(order.notes());
                entity.setHeld(order.held());
                entity.setSentToKitchenAt(order.sentToKitchenAt());
                entity.setClosedAt(order.closedAt());
            }

            OrderEntity savedOrder = orderRepo.saveAndFlush(entity);

            syncDiscounts(order, savedOrder.getId());
            syncItemsAndModifiers(order, savedOrder.getId());
            syncTaxes(order, savedOrder.getId());

            return loadAggregate(savedOrder);
        });
    }

    private void syncItemsAndModifiers(OrderAggregate order, UUID orderId) {
        Map<UUID, OrderItemEntity> existingById = itemRepo.findByOrderIdOrderByCreatedAtAsc(orderId)
                .stream().collect(Collectors.toMap(OrderItemEntity::getId, Function.identity()));
        Set<UUID> desiredIds = order.items().stream().map(OrderItemDetail::id).collect(Collectors.toSet());

        // Delete removed unsent items (first delete modifiers, then discounts referencing item, then item)
        for (OrderItemEntity existing : existingById.values()) {
            if (!desiredIds.contains(existing.getId())) {
                itemModRepo.deleteByOrderItemId(existing.getId());
                itemModRepo.flush();
                itemRepo.delete(existing);
            }
        }
        itemRepo.flush();

        for (OrderItemDetail item : order.items()) {
            OrderItemEntity existing = existingById.get(item.id());
            if (existing != null) {
                boolean wasSent = existing.isSentToKitchen();
                existing.setQuantity(item.quantity());
                existing.setTaxAmount(item.taxAmount());
                existing.setDiscountAmount(item.discountAmount());
                existing.setLineTotal(item.lineTotal());
                existing.setKitchenNotes(item.kitchenNotes());
                existing.setSentToKitchen(item.sentToKitchen());
                existing.setVoided(item.voided());
                itemRepo.saveAndFlush(existing);

                if (!wasSent) {
                    itemModRepo.deleteByOrderItemId(existing.getId());
                    itemModRepo.flush();
                    saveModifiers(order, existing.getId(), item.modifiers());
                }
            } else {
                OrderItemEntity created = new OrderItemEntity(
                        item.id(), order.tenantId().value(), order.branchId().value(), orderId,
                        item.productId(), item.skuSnapshot(), item.productNameSnapshot(),
                        item.variantNameSnapshot(), item.quantity(), item.unitPriceSnapshot(),
                        item.taxRateSnapshot(), item.taxAmount(), item.discountAmount(), item.lineTotal()
                );
                created.setProductVariantId(item.productVariantId());
                created.setTaxId(item.taxId());
                created.setTaxNameSnapshot(item.taxNameSnapshot());
                created.setTaxInclusive(item.taxInclusive());
                created.setKitchenNotes(item.kitchenNotes());
                created.setSentToKitchen(item.sentToKitchen());
                created.setVoided(item.voided());
                itemRepo.saveAndFlush(created);
                saveModifiers(order, created.getId(), item.modifiers());
            }
        }
    }

    private void saveModifiers(OrderAggregate order, UUID orderItemId, List<OrderItemModifierSnapshot> modifiers) {
        for (OrderItemModifierSnapshot m : modifiers) {
            itemModRepo.save(new OrderItemModifierEntity(
                    m.id(), order.tenantId().value(), order.branchId().value(), orderItemId,
                    m.modifierId(), m.modifierNameSnapshot(), m.unitPriceSnapshot(), m.quantity()
            ));
        }
        itemModRepo.flush();
    }

    private void syncDiscounts(OrderAggregate order, UUID orderId) {
        discountRepo.deleteByOrderId(orderId);
        discountRepo.flush();
        for (OrderDiscountSnapshot d : order.discounts()) {
            discountRepo.save(new OrderDiscountEntity(
                    d.id(), order.tenantId().value(), order.branchId().value(), orderId, d.orderItemId(),
                    d.discountCode(), d.discountName(), d.discountType(), d.discountValue(),
                    d.appliedAmount(), d.appliedByUserId(), d.approvedByUserId(), d.reason()
            ));
        }
        discountRepo.flush();
    }

    private void syncTaxes(OrderAggregate order, UUID orderId) {
        taxRepo.deleteByOrderId(orderId);
        taxRepo.flush();
        for (OrderTaxSnapshot t : order.taxes()) {
            taxRepo.save(new OrderTaxEntity(
                    t.id(), order.tenantId().value(), order.branchId().value(), orderId, t.taxId(),
                    t.taxNameSnapshot(), t.ratePercentSnapshot(), t.inclusive(),
                    t.taxableAmount(), t.taxAmount()
            ));
        }
        taxRepo.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderAggregate> findByIdScoped(UUID orderId, TenantId tenantId, BranchId branchId) {
        return orderRepo.findByIdAndTenantIdAndBranchId(orderId, tenantId.value(), branchId.value())
                .map(this::loadAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderSummaryDto> searchBranchOrders(
            TenantId tenantId,
            BranchId branchId,
            OrderStatus statusFilter,
            Boolean heldFilter,
            int page,
            int size
    ) {
        boolean applyStatus = statusFilter != null;
        String statusStr = applyStatus ? statusFilter.name() : "";
        boolean applyHeld = heldFilter != null;
        boolean heldVal = applyHeld && heldFilter;
        return orderRepo.searchSummaries(
                        tenantId.value(), branchId.value(), applyStatus, statusStr, applyHeld, heldVal, PageRequest.of(page, size))
                .stream()
                .map(p -> new OrderSummaryDto(
                        p.getId(), p.getOrderNumber(), p.getOrderType(), p.getStatus(),
                        p.getDiningTableId(), p.getTotalAmount(), p.isHeld(), p.getOpenedAt(), p.getVersion()
                ))
                .toList();
    }

    @Override
    @Transactional
    public void recordStatusHistory(
            UUID id, TenantId tenantId, BranchId branchId, UUID orderId,
            OrderStatus fromStatus, OrderStatus toStatus, UUID changedByUserId, String reason, Instant changedAt
    ) {
        historyRepo.saveAndFlush(new OrderStatusHistoryEntity(
                id, tenantId.value(), branchId.value(), orderId,
                fromStatus != null ? fromStatus.name() : null,
                toStatus.name(), changedByUserId, reason, changedAt
        ));
    }

    private OrderAggregate loadAggregate(OrderEntity e) {
        List<OrderItemDetail> items = itemRepo.findByOrderIdOrderByCreatedAtAsc(e.getId()).stream()
                .map(item -> {
                    List<OrderItemModifierSnapshot> mods = itemModRepo.findByOrderItemIdOrderByCreatedAtAsc(item.getId())
                            .stream()
                            .map(m -> new OrderItemModifierSnapshot(
                                    m.getId(), m.getModifierId(), m.getModifierNameSnapshot(),
                                    m.getUnitPriceSnapshot(), m.getQuantity()
                            ))
                            .toList();
                    return new OrderItemDetail(
                            item.getId(), item.getProductId(), item.getProductVariantId(), item.getTaxId(),
                            item.getTaxNameSnapshot(), item.isTaxInclusive(), item.getSkuSnapshot(),
                            item.getProductNameSnapshot(), item.getVariantNameSnapshot(), item.getQuantity(),
                            item.getUnitPriceSnapshot(), item.getTaxRateSnapshot(), item.getTaxAmount(),
                            item.getDiscountAmount(), item.getLineTotal(), item.getKitchenNotes(),
                            item.isSentToKitchen(), item.isVoided(), mods, item.getVersion()
                    );
                })
                .toList();

        List<OrderDiscountSnapshot> discounts = discountRepo.findByOrderIdOrderByCreatedAtAsc(e.getId()).stream()
                .map(d -> new OrderDiscountSnapshot(
                        d.getId(), d.getOrderItemId(), d.getDiscountCode(), d.getDiscountName(),
                        d.getDiscountType(), d.getDiscountValue(), d.getAppliedAmount(),
                        d.getAppliedByUserId(), d.getApprovedByUserId(), d.getReason()
                ))
                .toList();

        List<OrderTaxSnapshot> taxes = taxRepo.findByOrderIdOrderByCreatedAtAsc(e.getId()).stream()
                .map(t -> new OrderTaxSnapshot(
                        t.getId(), t.getTaxId(), t.getTaxNameSnapshot(), t.getRatePercentSnapshot(),
                        t.isInclusive(), t.getTaxableAmount(), t.getTaxAmount()
                ))
                .toList();

        return new OrderAggregate(
                e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                e.getTerminalId(), e.getDiningTableId(), e.getCreatedByUserId(), e.getAssignedWaiterId(),
                e.getOrderNumber(), OrderType.valueOf(e.getOrderType()), OrderStatus.valueOf(e.getStatus()),
                e.getCurrencyCode(), e.getSubtotalAmount(), e.getDiscountAmount(), e.getTaxAmount(),
                e.getTotalAmount(), e.getPaidAmount(), e.getGuestCount(), e.getCustomerReference(),
                e.getNotes(), e.isHeld(), e.getOpenedAt(), e.getSentToKitchenAt(), e.getClosedAt(),
                e.getVersion(), items, discounts, taxes
        );
    }
}
