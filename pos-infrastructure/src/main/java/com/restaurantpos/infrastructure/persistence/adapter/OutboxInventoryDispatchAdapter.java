package com.restaurantpos.infrastructure.persistence.adapter;

import com.restaurantpos.domain.port.InventoryDispatchPort;
import com.restaurantpos.domain.port.InventoryPayloadCodecPort;
import com.restaurantpos.infrastructure.persistence.entity.OrderItemEntity;
import com.restaurantpos.infrastructure.persistence.entity.OrderItemModifierEntity;
import com.restaurantpos.infrastructure.persistence.entity.OutboxEventEntity;
import com.restaurantpos.infrastructure.persistence.repository.OrderItemJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.OrderItemModifierJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.OutboxEventJpaRepository;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enriches kitchen start/refire events with authoritative OrderItem product/variant/modifier snapshots
 * and writes reliable inventory outbox events inside the calling transaction.
 */
@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxInventoryDispatchAdapter implements InventoryDispatchPort {

    private final OutboxEventJpaRepository outboxRepo;
    private final OrderItemJpaRepository orderItemRepo;
    private final OrderItemModifierJpaRepository modifierRepo;
    private final InventoryPayloadCodecPort codec;

    public OutboxInventoryDispatchAdapter(
            OutboxEventJpaRepository outboxRepo,
            OrderItemJpaRepository orderItemRepo,
            OrderItemModifierJpaRepository modifierRepo,
            InventoryPayloadCodecPort codec
    ) {
        this.outboxRepo = outboxRepo;
        this.orderItemRepo = orderItemRepo;
        this.modifierRepo = modifierRepo;
        this.codec = codec;
    }

    @Override
    @Transactional
    public void dispatchKitchenPrepStarted(
            TenantId tenantId,
            BranchId branchId,
            UUID orderId,
            UUID kitchenTicketId,
            UUID actorUserId,
            List<InventoryPayloadCodecPort.OrderPrepItemSnapshot> items
    ) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<InventoryPayloadCodecPort.OrderPrepItemSnapshot> enriched = enrich(orderId, items);
        UUID eventId = UUID.randomUUID();
        String json = codec.encodePrepStarted(new InventoryPayloadCodecPort.PrepStartedPayload(
                orderId, kitchenTicketId, actorUserId, enriched));
        outboxRepo.save(new OutboxEventEntity(
                eventId,
                tenantId.value(),
                branchId.value(),
                "KitchenTicket",
                kitchenTicketId,
                "INVENTORY_CONSUME_PREP",
                "inv-prep-" + kitchenTicketId + "-" + eventId,
                json,
                Instant.now()
        ));
    }

    @Override
    @Transactional
    public void dispatchKitchenRefire(
            TenantId tenantId,
            BranchId branchId,
            UUID orderId,
            UUID originalTicketId,
            UUID refireTicketId,
            UUID actorUserId,
            String reason,
            List<InventoryPayloadCodecPort.OrderPrepItemSnapshot> items
    ) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<InventoryPayloadCodecPort.OrderPrepItemSnapshot> enriched = enrich(orderId, items);
        UUID eventId = UUID.randomUUID();
        String json = codec.encodeTicketRefire(new InventoryPayloadCodecPort.TicketRefirePayload(
                orderId, originalTicketId, refireTicketId, actorUserId, reason, enriched));
        outboxRepo.save(new OutboxEventEntity(
                eventId,
                tenantId.value(),
                branchId.value(),
                "KitchenTicket",
                refireTicketId,
                "INVENTORY_REFIRE_TICKET",
                "inv-refire-" + refireTicketId,
                json,
                Instant.now()
        ));
    }

    private List<InventoryPayloadCodecPort.OrderPrepItemSnapshot> enrich(
            UUID orderId,
            List<InventoryPayloadCodecPort.OrderPrepItemSnapshot> raw
    ) {
        Map<UUID, OrderItemEntity> byOrderItemId = orderItemRepo.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .collect(Collectors.toMap(OrderItemEntity::getId, Function.identity()));

        return raw.stream()
                .map(in -> {
                    OrderItemEntity oi = byOrderItemId.get(in.orderItemId());
                    List<UUID> modIds = modifierRepo.findByOrderItemIdOrderByCreatedAtAsc(in.orderItemId()).stream()
                            .map(OrderItemModifierEntity::getModifierId)
                            .filter(java.util.Objects::nonNull)
                            .toList();
                    return new InventoryPayloadCodecPort.OrderPrepItemSnapshot(
                            in.orderItemId(),
                            in.kitchenTicketItemId(),
                            oi != null ? oi.getProductId() : in.productId(),
                            oi != null ? oi.getProductVariantId() : in.productVariantId(),
                            oi != null ? oi.getQuantity() : in.quantity(),
                            modIds
                    );
                })
                .toList();
    }
}
