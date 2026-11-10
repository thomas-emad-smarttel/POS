package com.restaurantpos.infrastructure.persistence.adapter;

import com.restaurantpos.domain.order.OrderItemSnapshot;
import com.restaurantpos.domain.order.OrderRecord;
import com.restaurantpos.domain.order.OrderStatus;
import com.restaurantpos.domain.order.OrderSummary;
import com.restaurantpos.domain.order.OrderType;
import com.restaurantpos.domain.port.OrderRepositoryPort;
import com.restaurantpos.infrastructure.persistence.DatabaseExceptionTranslator;
import com.restaurantpos.infrastructure.persistence.entity.OrderEntity;
import com.restaurantpos.infrastructure.persistence.entity.OrderItemEntity;
import com.restaurantpos.infrastructure.persistence.repository.OrderItemJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.OrderJpaRepository;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class OrderJpaAdapter implements OrderRepositoryPort {

    private final OrderJpaRepository orderRepository;
    private final OrderItemJpaRepository itemRepository;
    private final DatabaseExceptionTranslator translator;

    public OrderJpaAdapter(
            OrderJpaRepository orderRepository,
            OrderItemJpaRepository itemRepository,
            DatabaseExceptionTranslator translator
    ) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.translator = translator;
    }

    @Override
    @Transactional
    public OrderRecord save(OrderRecord order) {
        return translator.execute("save order", () -> {
            Optional<OrderEntity> existingOpt = orderRepository.findByIdAndTenantIdAndBranchId(
                    order.id(),
                    order.tenantId().value(),
                    order.branchId().value()
            );

            OrderEntity entity;
            if (existingOpt.isPresent()) {
                entity = existingOpt.get();
                if (entity.getVersion() != order.version()) {
                    throw new OptimisticLockingFailureException(
                            "Order " + order.id() + " version mismatch: expected " + order.version()
                                    + " but was " + entity.getVersion()
                    );
                }
                entity.setStatus(order.status().name());
                entity.setSubtotalAmount(order.subtotalAmount());
                entity.setDiscountAmount(order.discountAmount());
                entity.setTaxAmount(order.taxAmount());
                entity.setTotalAmount(order.totalAmount());
                entity.setPaidAmount(order.paidAmount());
            } else {
                entity = new OrderEntity(
                        order.id(),
                        order.tenantId().value(),
                        order.branchId().value(),
                        order.createdByUserId(),
                        order.orderNumber(),
                        order.orderType().name(),
                        order.status().name(),
                        order.currencyCode(),
                        order.subtotalAmount(),
                        order.discountAmount(),
                        order.taxAmount(),
                        order.totalAmount(),
                        order.paidAmount(),
                        order.guestCount(),
                        order.openedAt()
                );
            }

            OrderEntity savedOrder = orderRepository.saveAndFlush(entity);

            List<OrderItemEntity> existingItems = itemRepository.findByOrderIdOrderByCreatedAtAsc(savedOrder.getId());
            Set<UUID> existingItemIds = existingItems.stream().map(OrderItemEntity::getId).collect(Collectors.toSet());

            for (OrderItemSnapshot item : order.items()) {
                if (!existingItemIds.contains(item.id())) {
                    OrderItemEntity itemEntity = new OrderItemEntity(
                            item.id(),
                            order.tenantId().value(),
                            order.branchId().value(),
                            savedOrder.getId(),
                            item.productId(),
                            item.skuSnapshot(),
                            item.productNameSnapshot(),
                            item.variantNameSnapshot(),
                            item.quantity(),
                            item.unitPriceSnapshot(),
                            item.taxRateSnapshot(),
                            item.taxAmount(),
                            item.discountAmount(),
                            item.lineTotal()
                    );
                    itemRepository.saveAndFlush(itemEntity);
                }
            }

            List<OrderItemSnapshot> persistedItems = itemRepository.findByOrderIdOrderByCreatedAtAsc(savedOrder.getId())
                    .stream()
                    .map(OrderJpaAdapter::toItemDomain)
                    .toList();
            return toOrderDomain(savedOrder, persistedItems);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderRecord> findByIdScoped(UUID orderId, TenantId tenantId, BranchId branchId) {
        return orderRepository.findByIdAndTenantIdAndBranchId(orderId, tenantId.value(), branchId.value())
                .map(entity -> {
                    List<OrderItemSnapshot> items = itemRepository.findByOrderIdOrderByCreatedAtAsc(entity.getId())
                            .stream()
                            .map(OrderJpaAdapter::toItemDomain)
                            .toList();
                    return toOrderDomain(entity, items);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderSummary> findSummariesByBranch(TenantId tenantId, BranchId branchId, int page, int size) {
        return orderRepository.findSummariesByTenantAndBranch(
                        tenantId.value(),
                        branchId.value(),
                        PageRequest.of(page, size)
                )
                .stream()
                .map(p -> new OrderSummary(
                        p.getId(),
                        p.getOrderNumber(),
                        OrderType.valueOf(p.getOrderType()),
                        OrderStatus.valueOf(p.getStatus()),
                        p.getTotalAmount(),
                        p.getOpenedAt(),
                        p.getVersion()
                ))
                .toList();
    }

    private static OrderRecord toOrderDomain(OrderEntity entity, List<OrderItemSnapshot> items) {
        return new OrderRecord(
                entity.getId(),
                TenantId.of(entity.getTenantId()),
                BranchId.of(entity.getBranchId()),
                entity.getCreatedByUserId(),
                entity.getOrderNumber(),
                OrderType.valueOf(entity.getOrderType()),
                OrderStatus.valueOf(entity.getStatus()),
                entity.getCurrencyCode(),
                entity.getSubtotalAmount(),
                entity.getDiscountAmount(),
                entity.getTaxAmount(),
                entity.getTotalAmount(),
                entity.getPaidAmount(),
                entity.getGuestCount(),
                entity.getOpenedAt(),
                entity.getVersion(),
                items
        );
    }

    private static OrderItemSnapshot toItemDomain(OrderItemEntity entity) {
        return new OrderItemSnapshot(
                entity.getId(),
                entity.getProductId(),
                entity.getSkuSnapshot(),
                entity.getProductNameSnapshot(),
                entity.getVariantNameSnapshot(),
                entity.getQuantity(),
                entity.getUnitPriceSnapshot(),
                entity.getTaxRateSnapshot(),
                entity.getTaxAmount(),
                entity.getDiscountAmount(),
                entity.getLineTotal(),
                entity.getVersion()
        );
    }
}
