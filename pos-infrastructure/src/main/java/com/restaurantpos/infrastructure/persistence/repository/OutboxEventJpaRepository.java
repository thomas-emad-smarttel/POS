package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query(value = """
            SELECT *
            FROM outbox_events
            WHERE event_type IN ('ORDER_SENT_TO_KITCHEN', 'ORDER_CANCELLED_IN_KITCHEN')
              AND status = 'PENDING'
              AND next_attempt_at <= :now
            ORDER BY sequence_num ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> claimPendingKitchenEvents(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );

    @Query(value = """
            SELECT *
            FROM outbox_events
            WHERE event_type IN (
                'INVENTORY_RESERVE_ORDER',
                'INVENTORY_CONSUME_PREP',
                'INVENTORY_RELEASE_ORDER',
                'INVENTORY_REFIRE_TICKET'
            )
              AND status = 'PENDING'
              AND next_attempt_at <= :now
            ORDER BY sequence_num ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> claimPendingInventoryEvents(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );
}
