package com.restaurantpos.infrastructure.persistence.adapter;

import com.restaurantpos.domain.order.OrderAggregate;
import com.restaurantpos.domain.order.OrderItemDetail;
import com.restaurantpos.domain.port.KitchenDispatchPort;
import com.restaurantpos.domain.port.KitchenPayloadCodecPort;
import com.restaurantpos.infrastructure.persistence.entity.DiningTableEntity;
import com.restaurantpos.infrastructure.persistence.entity.OutboxEventEntity;
import com.restaurantpos.infrastructure.persistence.entity.ProductEntity;
import com.restaurantpos.infrastructure.persistence.entity.UserEntity;
import com.restaurantpos.infrastructure.persistence.repository.DiningTableJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.OutboxEventJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.ProductJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.UserJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persists immutable snapshot outbox events (`ORDER_SENT_TO_KITCHEN`, `ORDER_CANCELLED_IN_KITCHEN`)
 * inside the order transaction so KDS consumes reliable, idempotent events.
 */
@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxKitchenDispatchAdapter implements KitchenDispatchPort {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxKitchenDispatchAdapter.class);

    private final OutboxEventJpaRepository outboxRepository;
    private final ProductJpaRepository productRepo;
    private final DiningTableJpaRepository tableRepo;
    private final UserJpaRepository userRepo;
    private final KitchenPayloadCodecPort codec;

    public OutboxKitchenDispatchAdapter(
            OutboxEventJpaRepository outboxRepository,
            ProductJpaRepository productRepo,
            DiningTableJpaRepository tableRepo,
            UserJpaRepository userRepo,
            KitchenPayloadCodecPort codec
    ) {
        this.outboxRepository = outboxRepository;
        this.productRepo = productRepo;
        this.tableRepo = tableRepo;
        this.userRepo = userRepo;
        this.codec = codec;
    }

    @Override
    @Transactional
    public void dispatchOrderToKitchen(OrderAggregate order) {
        UUID eventId = UUID.randomUUID();
        String tableLabel = null;
        if (order.diningTableId() != null) {
            tableLabel = tableRepo.findByIdAndTenantIdAndBranchId(
                            order.diningTableId(), order.tenantId().value(), order.branchId().value())
                    .map(DiningTableEntity::getTableCode)
                    .orElse(null);
        }
        UUID waiterId = order.assignedWaiterId() != null ? order.assignedWaiterId() : order.createdByUserId();
        String waiterName = userRepo.findByIdAndTenantId(waiterId, order.tenantId().value())
                .map(UserEntity::getFullName)
                .orElse(null);

        List<KitchenPayloadCodecPort.DispatchedItemSnapshot> items = order.items().stream()
                .filter(i -> !i.voided())
                .map(i -> toItemSnapshot(order, i))
                .toList();

        String payload = codec.encodeOrderDispatched(new KitchenPayloadCodecPort.DispatchedOrderSnapshot(
                order.id(),
                order.orderNumber(),
                order.orderType().name(),
                tableLabel,
                waiterId,
                waiterName,
                items
        ));

        outboxRepository.save(new OutboxEventEntity(
                eventId,
                order.tenantId().value(),
                order.branchId().value(),
                "Order",
                order.id(),
                "ORDER_SENT_TO_KITCHEN",
                "kitchen-dispatch-" + order.id() + "-" + eventId,
                payload,
                Instant.now()
        ));
        LOG.info("Dispatched order {} to kitchen outbox eventId={}", order.orderNumber(), eventId);
    }

    @Override
    @Transactional
    public void dispatchOrderCancelled(OrderAggregate order, String reason) {
        UUID eventId = UUID.randomUUID();
        String payload = codec.encodeOrderCancelled(new KitchenPayloadCodecPort.CancelledOrderSnapshot(
                order.id(), order.orderNumber(), reason
        ));
        outboxRepository.save(new OutboxEventEntity(
                eventId,
                order.tenantId().value(),
                order.branchId().value(),
                "Order",
                order.id(),
                "ORDER_CANCELLED_IN_KITCHEN",
                "kitchen-cancel-" + order.id() + "-" + eventId,
                payload,
                Instant.now()
        ));
        LOG.info("Dispatched kitchen cancellation for order {} eventId={}", order.orderNumber(), eventId);
    }

    private KitchenPayloadCodecPort.DispatchedItemSnapshot toItemSnapshot(OrderAggregate order, OrderItemDetail item) {
        UUID categoryId = item.productId() != null
                ? productRepo.findByIdAndTenantId(item.productId(), order.tenantId().value())
                        .map(ProductEntity::getCategoryId)
                        .orElse(null)
                : null;
        List<String> mods = item.modifiers().stream()
                .map(m -> (m.quantity() > 1 ? m.quantity() + "x " : "") + m.modifierNameSnapshot())
                .toList();
        return new KitchenPayloadCodecPort.DispatchedItemSnapshot(
                item.id(),
                item.productId(),
                categoryId,
                item.productNameSnapshot(),
                item.variantNameSnapshot(),
                item.quantity(),
                mods,
                null,
                item.kitchenNotes()
        );
    }
}
