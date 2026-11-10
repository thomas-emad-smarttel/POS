package com.restaurantpos.domain.port;

import com.restaurantpos.domain.kitchen.KitchenDisplayConfiguration;
import com.restaurantpos.domain.kitchen.KitchenPrintJob;
import com.restaurantpos.domain.kitchen.KitchenRoutingRule;
import com.restaurantpos.domain.kitchen.KitchenStation;
import com.restaurantpos.domain.kitchen.KitchenStationAssignment;
import com.restaurantpos.domain.kitchen.KitchenTicket;
import com.restaurantpos.domain.kitchen.KitchenTicketEvent;
import com.restaurantpos.domain.kitchen.KitchenUnroutedItem;
import com.restaurantpos.domain.kitchen.OutboxWorkItem;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface KitchenRepositoryPort {

    KitchenStation saveStation(KitchenStation station);

    Optional<KitchenStation> findStationScoped(UUID stationId, TenantId tenantId, BranchId branchId);

    List<KitchenStation> findStationsByBranch(TenantId tenantId, BranchId branchId);

    KitchenStationAssignment saveAssignment(KitchenStationAssignment assignment);

    List<KitchenStationAssignment> findAssignmentsByStation(UUID stationId, TenantId tenantId, BranchId branchId);

    KitchenRoutingRule saveRoutingRule(KitchenRoutingRule rule);

    Optional<KitchenRoutingRule> findRoutingRuleScoped(UUID ruleId, TenantId tenantId, BranchId branchId);

    List<KitchenRoutingRule> findRoutingRulesByBranch(TenantId tenantId, BranchId branchId);

    KitchenDisplayConfiguration saveDisplayConfig(KitchenDisplayConfiguration config);

    Optional<KitchenDisplayConfiguration> findDisplayConfig(TenantId tenantId, BranchId branchId);

    KitchenTicket saveTicket(KitchenTicket ticket);

    Optional<KitchenTicket> findTicketScoped(UUID ticketId, TenantId tenantId, BranchId branchId);

    List<KitchenTicket> findActiveTicketsByStation(TenantId tenantId, BranchId branchId, UUID stationId);

    List<KitchenTicket> findActiveTicketsByBranch(TenantId tenantId, BranchId branchId);

    List<KitchenTicket> findTicketsByOrderScoped(UUID orderId, TenantId tenantId, BranchId branchId);

    Set<UUID> findDispatchedOrderItemIdsForOrder(UUID orderId, TenantId tenantId, BranchId branchId);

    KitchenTicketEvent appendTicketEvent(KitchenTicketEvent event);

    List<KitchenTicketEvent> findTicketEvents(UUID ticketId, TenantId tenantId, BranchId branchId);

    KitchenUnroutedItem saveUnroutedItem(KitchenUnroutedItem item);

    Optional<KitchenUnroutedItem> findUnroutedItemScoped(UUID itemId, TenantId tenantId, BranchId branchId);

    List<KitchenUnroutedItem> findOpenUnroutedItems(TenantId tenantId, BranchId branchId);

    KitchenPrintJob savePrintJob(KitchenPrintJob job);

    Optional<KitchenPrintJob> findPrintJobScoped(UUID jobId, TenantId tenantId, BranchId branchId);

    Optional<KitchenPrintJob> findPrintJobByIdempotency(TenantId tenantId, BranchId branchId, String idempotencyKey);

    List<KitchenPrintJob> findFailedPrintJobs(TenantId tenantId, BranchId branchId);

    List<KitchenPrintJob> findPrintJobsByTicket(UUID ticketId, TenantId tenantId, BranchId branchId);

    // Outbox consumption & idempotency tracking
    List<OutboxWorkItem> claimPendingKitchenOutboxEvents(int batchSize, Instant now);

    boolean isOutboxEventProcessed(UUID outboxEventId);

    void markOutboxEventProcessed(UUID outboxEventId, TenantId tenantId, BranchId branchId, String eventType, Instant processedAt);

    void markOutboxEventPublished(UUID outboxEventId, Instant publishedAt);

    void markOutboxEventRetry(UUID outboxEventId, int nextRetryCount, Instant nextAttemptAt, String status);
}
