package com.restaurantpos.infrastructure.persistence.adapter;

import com.restaurantpos.domain.kitchen.KitchenDisplayConfiguration;
import com.restaurantpos.domain.kitchen.KitchenPrintJob;
import com.restaurantpos.domain.kitchen.KitchenPriorityLevel;
import com.restaurantpos.domain.kitchen.KitchenRoutingRule;
import com.restaurantpos.domain.kitchen.KitchenStation;
import com.restaurantpos.domain.kitchen.KitchenStationAssignment;
import com.restaurantpos.domain.kitchen.KitchenTicket;
import com.restaurantpos.domain.kitchen.KitchenTicketEvent;
import com.restaurantpos.domain.kitchen.KitchenTicketItem;
import com.restaurantpos.domain.kitchen.KitchenTicketItemStatus;
import com.restaurantpos.domain.kitchen.KitchenTicketStatus;
import com.restaurantpos.domain.kitchen.KitchenUnroutedItem;
import com.restaurantpos.domain.kitchen.OutboxWorkItem;
import com.restaurantpos.domain.port.KitchenRepositoryPort;
import com.restaurantpos.infrastructure.persistence.DatabaseExceptionTranslator;
import com.restaurantpos.infrastructure.persistence.entity.KitchenDisplayConfigEntity;
import com.restaurantpos.infrastructure.persistence.entity.KitchenPrintJobEntity;
import com.restaurantpos.infrastructure.persistence.entity.KitchenProcessedEventEntity;
import com.restaurantpos.infrastructure.persistence.entity.KitchenRoutingRuleEntity;
import com.restaurantpos.infrastructure.persistence.entity.KitchenStationAssignmentEntity;
import com.restaurantpos.infrastructure.persistence.entity.KitchenStationEntity;
import com.restaurantpos.infrastructure.persistence.entity.KitchenTicketEntity;
import com.restaurantpos.infrastructure.persistence.entity.KitchenTicketEventEntity;
import com.restaurantpos.infrastructure.persistence.entity.KitchenTicketItemEntity;
import com.restaurantpos.infrastructure.persistence.entity.KitchenUnroutedItemEntity;
import com.restaurantpos.infrastructure.persistence.entity.OutboxEventEntity;
import com.restaurantpos.infrastructure.persistence.repository.KitchenDisplayConfigJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.KitchenPrintJobJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.KitchenProcessedEventJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.KitchenRoutingRuleJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.KitchenStationAssignmentJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.KitchenStationJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.KitchenTicketEventJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.KitchenTicketItemJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.KitchenTicketJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.KitchenUnroutedItemJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.OutboxEventJpaRepository;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class KitchenJpaAdapter implements KitchenRepositoryPort {

    private static final List<String> ACTIVE_STATUSES = List.of(
            "NEW", "QUEUED", "ACCEPTED", "IN_PROGRESS", "PREPARING", "READY", "ON_HOLD"
    );

    private final KitchenStationJpaRepository stationRepo;
    private final KitchenStationAssignmentJpaRepository assignmentRepo;
    private final KitchenRoutingRuleJpaRepository ruleRepo;
    private final KitchenDisplayConfigJpaRepository displayConfigRepo;
    private final KitchenTicketJpaRepository ticketRepo;
    private final KitchenTicketItemJpaRepository itemRepo;
    private final KitchenTicketEventJpaRepository eventRepo;
    private final KitchenProcessedEventJpaRepository processedRepo;
    private final KitchenUnroutedItemJpaRepository unroutedRepo;
    private final KitchenPrintJobJpaRepository printJobRepo;
    private final OutboxEventJpaRepository outboxRepo;
    private final KitchenPayloadCodecAdapter codec;
    private final DatabaseExceptionTranslator translator;

    public KitchenJpaAdapter(
            KitchenStationJpaRepository stationRepo,
            KitchenStationAssignmentJpaRepository assignmentRepo,
            KitchenRoutingRuleJpaRepository ruleRepo,
            KitchenDisplayConfigJpaRepository displayConfigRepo,
            KitchenTicketJpaRepository ticketRepo,
            KitchenTicketItemJpaRepository itemRepo,
            KitchenTicketEventJpaRepository eventRepo,
            KitchenProcessedEventJpaRepository processedRepo,
            KitchenUnroutedItemJpaRepository unroutedRepo,
            KitchenPrintJobJpaRepository printJobRepo,
            OutboxEventJpaRepository outboxRepo,
            KitchenPayloadCodecAdapter codec,
            DatabaseExceptionTranslator translator
    ) {
        this.stationRepo = stationRepo;
        this.assignmentRepo = assignmentRepo;
        this.ruleRepo = ruleRepo;
        this.displayConfigRepo = displayConfigRepo;
        this.ticketRepo = ticketRepo;
        this.itemRepo = itemRepo;
        this.eventRepo = eventRepo;
        this.processedRepo = processedRepo;
        this.unroutedRepo = unroutedRepo;
        this.printJobRepo = printJobRepo;
        this.outboxRepo = outboxRepo;
        this.codec = codec;
        this.translator = translator;
    }

    @Override
    @Transactional
    public KitchenStation saveStation(KitchenStation s) {
        KitchenStationEntity entity = stationRepo.findByIdAndTenantIdAndBranchId(
                        s.id(), s.tenantId().value(), s.branchId().value())
                .map(existing -> {
                    existing.setCode(s.code());
                    existing.setName(s.name());
                    existing.setStationType(s.stationType());
                    existing.setPrinterDeviceId(s.printerDeviceId());
                    existing.setDisplayOrder(s.displayOrder());
                    existing.setExpectedPrepMinutes(s.expectedPrepMinutes());
                    existing.setExpoEnabled(s.expoEnabled());
                    existing.setPaperWidth(s.paperWidth());
                    existing.setReceiptLanguage(s.receiptLanguage());
                    existing.setReceiptEncoding(s.receiptEncoding());
                    existing.setDefaultStation(s.defaultStation());
                    existing.setActive(s.active());
                    return existing;
                })
                .orElseGet(() -> {
                    KitchenStationEntity c = new KitchenStationEntity(
                            s.id(), s.tenantId().value(), s.branchId().value(), s.code(), s.name());
                    c.setStationType(s.stationType());
                    c.setPrinterDeviceId(s.printerDeviceId());
                    c.setDisplayOrder(s.displayOrder());
                    c.setExpectedPrepMinutes(s.expectedPrepMinutes());
                    c.setExpoEnabled(s.expoEnabled());
                    c.setPaperWidth(s.paperWidth());
                    c.setReceiptLanguage(s.receiptLanguage());
                    c.setReceiptEncoding(s.receiptEncoding());
                    c.setDefaultStation(s.defaultStation());
                    c.setActive(s.active());
                    return c;
                });
        return toStationDomain(translator.execute("save kitchen station", () -> stationRepo.saveAndFlush(entity)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KitchenStation> findStationScoped(UUID stationId, TenantId tenantId, BranchId branchId) {
        return stationRepo.findByIdAndTenantIdAndBranchId(stationId, tenantId.value(), branchId.value())
                .map(KitchenJpaAdapter::toStationDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KitchenStation> findStationsByBranch(TenantId tenantId, BranchId branchId) {
        return stationRepo.findByTenantIdAndBranchIdOrderByDisplayOrderAscNameAsc(tenantId.value(), branchId.value())
                .stream().map(KitchenJpaAdapter::toStationDomain).toList();
    }

    @Override
    @Transactional
    public KitchenStationAssignment saveAssignment(KitchenStationAssignment a) {
        KitchenStationAssignmentEntity saved = translator.execute("save kitchen assignment", () ->
                assignmentRepo.saveAndFlush(new KitchenStationAssignmentEntity(
                        a.id(), a.tenantId().value(), a.branchId().value(), a.kitchenStationId(),
                        a.userId(), a.terminalId(), a.active()
                )));
        return new KitchenStationAssignment(
                saved.getId(), TenantId.of(saved.getTenantId()), BranchId.of(saved.getBranchId()),
                saved.getKitchenStationId(), saved.getUserId(), saved.getTerminalId(), saved.isActive()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<KitchenStationAssignment> findAssignmentsByStation(UUID stationId, TenantId tenantId, BranchId branchId) {
        return assignmentRepo.findByKitchenStationIdAndTenantIdAndBranchId(stationId, tenantId.value(), branchId.value())
                .stream()
                .map(e -> new KitchenStationAssignment(
                        e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                        e.getKitchenStationId(), e.getUserId(), e.getTerminalId(), e.isActive()
                ))
                .toList();
    }

    @Override
    @Transactional
    public KitchenRoutingRule saveRoutingRule(KitchenRoutingRule r) {
        KitchenRoutingRuleEntity entity = ruleRepo.findByIdAndTenantIdAndBranchId(
                        r.id(), r.tenantId().value(), r.branchId().value())
                .map(existing -> {
                    existing.setKitchenStationId(r.kitchenStationId());
                    existing.setPrecedence(r.precedence());
                    existing.setExpectedPrepMinutesOverride(r.expectedPrepMinutesOverride());
                    existing.setActive(r.active());
                    return existing;
                })
                .orElseGet(() -> new KitchenRoutingRuleEntity(
                        r.id(), r.tenantId().value(), r.branchId().value(), r.kitchenStationId(),
                        r.productId(), r.categoryId(), r.precedence(),
                        r.expectedPrepMinutesOverride(), r.active()
                ));
        KitchenRoutingRuleEntity saved = translator.execute("save routing rule", () -> ruleRepo.saveAndFlush(entity));
        return toRuleDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KitchenRoutingRule> findRoutingRuleScoped(UUID ruleId, TenantId tenantId, BranchId branchId) {
        return ruleRepo.findByIdAndTenantIdAndBranchId(ruleId, tenantId.value(), branchId.value())
                .map(KitchenJpaAdapter::toRuleDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KitchenRoutingRule> findRoutingRulesByBranch(TenantId tenantId, BranchId branchId) {
        return ruleRepo.findByTenantIdAndBranchIdOrderByPrecedenceAsc(tenantId.value(), branchId.value())
                .stream().map(KitchenJpaAdapter::toRuleDomain).toList();
    }

    @Override
    @Transactional
    public KitchenDisplayConfiguration saveDisplayConfig(KitchenDisplayConfiguration c) {
        KitchenDisplayConfigEntity entity = displayConfigRepo.findByTenantIdAndBranchId(
                        c.tenantId().value(), c.branchId().value())
                .map(existing -> {
                    existing.setExpoEnabled(c.expoEnabled());
                    existing.setDefaultSortMode(c.defaultSortMode());
                    existing.setDefaultExpectedPrepMinutes(c.defaultExpectedPrepMinutes());
                    existing.setAutoPrintOnDispatch(c.autoPrintOnDispatch());
                    return existing;
                })
                .orElseGet(() -> new KitchenDisplayConfigEntity(
                        c.id(), c.tenantId().value(), c.branchId().value(), c.expoEnabled(),
                        c.defaultSortMode(), c.defaultExpectedPrepMinutes(), c.autoPrintOnDispatch()
                ));
        KitchenDisplayConfigEntity saved = translator.execute("save kitchen display config",
                () -> displayConfigRepo.saveAndFlush(entity));
        return new KitchenDisplayConfiguration(
                saved.getId(), TenantId.of(saved.getTenantId()), BranchId.of(saved.getBranchId()),
                saved.isExpoEnabled(), saved.getDefaultSortMode(),
                saved.getDefaultExpectedPrepMinutes(), saved.isAutoPrintOnDispatch(), saved.getVersion()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KitchenDisplayConfiguration> findDisplayConfig(TenantId tenantId, BranchId branchId) {
        return displayConfigRepo.findByTenantIdAndBranchId(tenantId.value(), branchId.value())
                .map(e -> new KitchenDisplayConfiguration(
                        e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                        e.isExpoEnabled(), e.getDefaultSortMode(),
                        e.getDefaultExpectedPrepMinutes(), e.isAutoPrintOnDispatch(), e.getVersion()
                ));
    }

    @Override
    @Transactional
    public KitchenTicket saveTicket(KitchenTicket t) {
        KitchenTicketEntity entity = ticketRepo.findByIdAndTenantIdAndBranchId(
                        t.id(), t.tenantId().value(), t.branchId().value())
                .map(existing -> {
                    existing.setStatus(t.status().name());
                    existing.setPriorityLevel(t.priorityLevel().name());
                    existing.setPriority(t.priorityScore());
                    existing.setExpectedPrepMinutes(t.expectedPrepMinutes());
                    existing.setAcceptedAt(t.acceptedAt());
                    existing.setStartedAt(t.startedAt());
                    existing.setReadyAt(t.readyAt());
                    existing.setBumpedAt(t.bumpedAt());
                    existing.setCancelledAt(t.cancelledAt());
                    existing.setCancelledByUserId(t.cancelledByUserId());
                    existing.setHoldReason(t.holdReason());
                    existing.setCancelReason(t.cancelReason());
                    return existing;
                })
                .orElseGet(() -> {
                    KitchenTicketEntity c = new KitchenTicketEntity(
                            t.id(), t.tenantId().value(), t.branchId().value(), t.orderId(),
                            t.orderNumberSnapshot(), t.orderTypeSnapshot(), t.tableLabelSnapshot(),
                            t.waiterId(), t.waiterNameSnapshot(), t.kitchenStationId(), t.sourceOutboxEventId(),
                            t.ticketNumber(), t.ticketKind(), t.status().name(),
                            t.priorityLevel().name(), t.priorityScore(), t.expectedPrepMinutes(), t.firedAt()
                    );
                    c.setAcceptedAt(t.acceptedAt());
                    c.setStartedAt(t.startedAt());
                    c.setReadyAt(t.readyAt());
                    c.setBumpedAt(t.bumpedAt());
                    c.setCancelledAt(t.cancelledAt());
                    c.setCancelledByUserId(t.cancelledByUserId());
                    c.setHoldReason(t.holdReason());
                    c.setCancelReason(t.cancelReason());
                    return c;
                });

        KitchenTicketEntity saved = translator.execute("save kitchen ticket", () -> ticketRepo.saveAndFlush(entity));

        Map<UUID, KitchenTicketItemEntity> existingItems = itemRepo.findByKitchenTicketIdOrderByCreatedAtAsc(saved.getId())
                .stream().collect(Collectors.toMap(KitchenTicketItemEntity::getId, Function.identity()));

        for (KitchenTicketItem item : t.items()) {
            KitchenTicketItemEntity itemEntity = existingItems.get(item.id());
            if (itemEntity != null) {
                itemEntity.setStatus(item.status().name());
                itemEntity.setStartedAt(item.startedAt());
                itemEntity.setReadyAt(item.readyAt());
                itemEntity.setServedAt(item.servedAt());
                itemEntity.setCancelReason(item.cancelReason());
            } else {
                itemEntity = new KitchenTicketItemEntity(
                        item.id(), t.tenantId().value(), t.branchId().value(), saved.getId(),
                        item.orderItemId(), item.itemNameSnapshot(), item.variantNameSnapshot(),
                        item.quantity(), codec.encodeModifiers(item.selectedModifiers()),
                        item.prepInstructions(), item.notes(), item.expectedPrepMinutes(), item.status().name()
                );
                itemEntity.setStartedAt(item.startedAt());
                itemEntity.setReadyAt(item.readyAt());
                itemEntity.setServedAt(item.servedAt());
                itemEntity.setCancelReason(item.cancelReason());
            }
            itemRepo.saveAndFlush(itemEntity);
        }

        return loadTicketAggregate(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KitchenTicket> findTicketScoped(UUID ticketId, TenantId tenantId, BranchId branchId) {
        return ticketRepo.findByIdAndTenantIdAndBranchId(ticketId, tenantId.value(), branchId.value())
                .map(this::loadTicketAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KitchenTicket> findActiveTicketsByStation(TenantId tenantId, BranchId branchId, UUID stationId) {
        return ticketRepo.findByTenantIdAndBranchIdAndKitchenStationIdAndStatusInOrderByFiredAtAsc(
                        tenantId.value(), branchId.value(), stationId, ACTIVE_STATUSES)
                .stream().map(this::loadTicketAggregate).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<KitchenTicket> findActiveTicketsByBranch(TenantId tenantId, BranchId branchId) {
        return ticketRepo.findByTenantIdAndBranchIdAndStatusInOrderByFiredAtAsc(
                        tenantId.value(), branchId.value(), ACTIVE_STATUSES)
                .stream().map(this::loadTicketAggregate).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<KitchenTicket> findTicketsByOrderScoped(UUID orderId, TenantId tenantId, BranchId branchId) {
        return ticketRepo.findByOrderIdAndTenantIdAndBranchIdOrderByFiredAtAsc(
                        orderId, tenantId.value(), branchId.value())
                .stream().map(this::loadTicketAggregate).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findDispatchedOrderItemIdsForOrder(UUID orderId, TenantId tenantId, BranchId branchId) {
        return new HashSet<>(itemRepo.findDispatchedOrderItemIdsForOrder(orderId, tenantId.value(), branchId.value()));
    }

    @Override
    @Transactional
    public KitchenTicketEvent appendTicketEvent(KitchenTicketEvent e) {
        KitchenTicketEventEntity saved = translator.execute("append kitchen ticket event", () ->
                eventRepo.saveAndFlush(new KitchenTicketEventEntity(
                        e.id(), e.tenantId().value(), e.branchId().value(),
                        e.kitchenTicketId(), e.kitchenTicketItemId(), e.actorUserId(),
                        e.eventType(), e.previousStatus(), e.newStatus(),
                        e.reason(), e.detailsJson(), e.occurredAt()
                )));
        return new KitchenTicketEvent(
                saved.getId(), TenantId.of(saved.getTenantId()), BranchId.of(saved.getBranchId()),
                saved.getKitchenTicketId(), saved.getKitchenTicketItemId(), saved.getActorUserId(),
                saved.getEventType(), saved.getPreviousStatus(), saved.getNewStatus(),
                saved.getReason(), saved.getDetailsJson(), saved.getOccurredAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<KitchenTicketEvent> findTicketEvents(UUID ticketId, TenantId tenantId, BranchId branchId) {
        return eventRepo.findByKitchenTicketIdAndTenantIdAndBranchIdOrderByOccurredAtAsc(
                        ticketId, tenantId.value(), branchId.value())
                .stream()
                .map(saved -> new KitchenTicketEvent(
                        saved.getId(), TenantId.of(saved.getTenantId()), BranchId.of(saved.getBranchId()),
                        saved.getKitchenTicketId(), saved.getKitchenTicketItemId(), saved.getActorUserId(),
                        saved.getEventType(), saved.getPreviousStatus(), saved.getNewStatus(),
                        saved.getReason(), saved.getDetailsJson(), saved.getOccurredAt()
                ))
                .toList();
    }

    @Override
    @Transactional
    public KitchenUnroutedItem saveUnroutedItem(KitchenUnroutedItem u) {
        KitchenUnroutedItemEntity entity = unroutedRepo.findByIdAndTenantIdAndBranchId(
                        u.id(), u.tenantId().value(), u.branchId().value())
                .map(existing -> {
                    existing.setStatus(u.status());
                    existing.setResolvedStationId(u.resolvedStationId());
                    existing.setResolvedByUserId(u.resolvedByUserId());
                    existing.setResolvedAt(u.resolvedAt());
                    return existing;
                })
                .orElseGet(() -> new KitchenUnroutedItemEntity(
                        u.id(), u.tenantId().value(), u.branchId().value(), u.outboxEventId(),
                        u.orderId(), u.orderNumber(), u.orderItemId(), u.productId(),
                        u.productNameSnapshot(), u.quantity(), u.modifiersSummary(), u.status(), u.createdAt()
                ));
        KitchenUnroutedItemEntity saved = translator.execute("save unrouted item", () -> unroutedRepo.saveAndFlush(entity));
        return toUnroutedDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KitchenUnroutedItem> findUnroutedItemScoped(UUID itemId, TenantId tenantId, BranchId branchId) {
        return unroutedRepo.findByIdAndTenantIdAndBranchId(itemId, tenantId.value(), branchId.value())
                .map(KitchenJpaAdapter::toUnroutedDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KitchenUnroutedItem> findOpenUnroutedItems(TenantId tenantId, BranchId branchId) {
        return unroutedRepo.findByTenantIdAndBranchIdAndStatusOrderByCreatedAtAsc(
                        tenantId.value(), branchId.value(), "OPEN")
                .stream().map(KitchenJpaAdapter::toUnroutedDomain).toList();
    }

    @Override
    @Transactional
    public KitchenPrintJob savePrintJob(KitchenPrintJob j) {
        KitchenPrintJobEntity entity = printJobRepo.findByIdAndTenantIdAndBranchId(
                        j.id(), j.tenantId().value(), j.branchId().value())
                .map(existing -> {
                    existing.setPrinterName(j.printerName());
                    existing.setStatus(j.status());
                    existing.setAttemptCount(j.attemptCount());
                    existing.setErrorMessage(j.errorMessage());
                    existing.setLastAttemptAt(j.lastAttemptAt());
                    return existing;
                })
                .orElseGet(() -> new KitchenPrintJobEntity(
                        j.id(), j.tenantId().value(), j.branchId().value(), j.kitchenTicketId(),
                        j.kitchenStationId(), j.jobType(), j.printerName(), j.language(),
                        j.paperWidth(), j.encoding(), j.renderedText(), j.escPosBase64(),
                        j.status(), j.attemptCount(), j.errorMessage(), j.idempotencyKey(),
                        j.createdAt(), j.lastAttemptAt()
                ));
        KitchenPrintJobEntity saved = translator.execute("save kitchen print job", () -> printJobRepo.saveAndFlush(entity));
        return toPrintJobDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KitchenPrintJob> findPrintJobScoped(UUID jobId, TenantId tenantId, BranchId branchId) {
        return printJobRepo.findByIdAndTenantIdAndBranchId(jobId, tenantId.value(), branchId.value())
                .map(KitchenJpaAdapter::toPrintJobDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KitchenPrintJob> findPrintJobByIdempotency(TenantId tenantId, BranchId branchId, String idempotencyKey) {
        return printJobRepo.findByTenantIdAndBranchIdAndIdempotencyKey(tenantId.value(), branchId.value(), idempotencyKey)
                .map(KitchenJpaAdapter::toPrintJobDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KitchenPrintJob> findFailedPrintJobs(TenantId tenantId, BranchId branchId) {
        return printJobRepo.findByTenantIdAndBranchIdAndStatusInOrderByLastAttemptAtDesc(
                        tenantId.value(), branchId.value(), List.of("FAILED", "PRINTER_UNAVAILABLE"))
                .stream().map(KitchenJpaAdapter::toPrintJobDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<KitchenPrintJob> findPrintJobsByTicket(UUID ticketId, TenantId tenantId, BranchId branchId) {
        return printJobRepo.findByKitchenTicketIdAndTenantIdAndBranchIdOrderByCreatedAtAsc(
                        ticketId, tenantId.value(), branchId.value())
                .stream().map(KitchenJpaAdapter::toPrintJobDomain).toList();
    }

    @Override
    @Transactional
    public List<OutboxWorkItem> claimPendingKitchenOutboxEvents(int batchSize, Instant now) {
        List<OutboxEventEntity> entities = outboxRepo.claimPendingKitchenEvents(now, batchSize);
        for (OutboxEventEntity e : entities) {
            e.setStatus("IN_FLIGHT");
        }
        outboxRepo.saveAllAndFlush(entities);
        return entities.stream()
                .map(e -> new OutboxWorkItem(
                        e.getId(), TenantId.of(e.getTenantId()),
                        e.getBranchId() != null ? BranchId.of(e.getBranchId()) : null,
                        e.getAggregateType(), e.getAggregateId(), e.getEventType(),
                        e.getIdempotencyKey(), e.getPayloadJson(), e.getStatus(),
                        e.getRetryCount(), e.getNextAttemptAt(), e.getCreatedAt()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOutboxEventProcessed(UUID outboxEventId) {
        return processedRepo.existsById(outboxEventId);
    }

    @Override
    @Transactional
    public void markOutboxEventProcessed(UUID outboxEventId, TenantId tenantId, BranchId branchId, String eventType, Instant processedAt) {
        if (!processedRepo.existsById(outboxEventId)) {
            processedRepo.saveAndFlush(new KitchenProcessedEventEntity(
                    outboxEventId, tenantId.value(), branchId != null ? branchId.value() : null, eventType, processedAt
            ));
        }
    }

    @Override
    @Transactional
    public void markOutboxEventPublished(UUID outboxEventId, Instant publishedAt) {
        outboxRepo.findById(outboxEventId).ifPresent(e -> {
            e.setStatus("PUBLISHED");
            e.setPublishedAt(publishedAt);
            outboxRepo.saveAndFlush(e);
        });
    }

    @Override
    @Transactional
    public void markOutboxEventRetry(UUID outboxEventId, int nextRetryCount, Instant nextAttemptAt, String status) {
        outboxRepo.findById(outboxEventId).ifPresent(e -> {
            e.setRetryCount(nextRetryCount);
            e.setNextAttemptAt(nextAttemptAt);
            e.setStatus(status);
            outboxRepo.saveAndFlush(e);
        });
    }

    private KitchenTicket loadTicketAggregate(KitchenTicketEntity e) {
        List<KitchenTicketItem> items = itemRepo.findByKitchenTicketIdOrderByCreatedAtAsc(e.getId()).stream()
                .map(i -> new KitchenTicketItem(
                        i.getId(), TenantId.of(i.getTenantId()), BranchId.of(i.getBranchId()),
                        i.getKitchenTicketId(), i.getOrderItemId(), i.getItemNameSnapshot(),
                        i.getVariantNameSnapshot(), i.getQuantity(),
                        codec.decodeModifiers(i.getModifiersSnapshotJson()),
                        i.getPrepInstructions(), i.getNotes(), i.getExpectedPrepMinutes(),
                        KitchenTicketItemStatus.valueOf(i.getStatus()),
                        i.getStartedAt(), i.getReadyAt(), i.getServedAt(), i.getCancelReason(),
                        i.getVersion()
                ))
                .toList();
        return new KitchenTicket(
                e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                e.getOrderId(), e.getOrderNumberSnapshot(), e.getOrderTypeSnapshot(),
                e.getTableLabelSnapshot(), e.getWaiterId(), e.getWaiterNameSnapshot(),
                e.getKitchenStationId(), e.getSourceOutboxEventId(), e.getTicketNumber(),
                e.getTicketKind(), KitchenTicketStatus.valueOf(e.getStatus()),
                KitchenPriorityLevel.valueOf(e.getPriorityLevel()), e.getPriority(),
                e.getExpectedPrepMinutes(), e.getFiredAt(), e.getAcceptedAt(), e.getStartedAt(),
                e.getReadyAt(), e.getBumpedAt(), e.getCancelledAt(), e.getCancelledByUserId(),
                e.getHoldReason(), e.getCancelReason(), e.getVersion(), items
        );
    }

    private static KitchenStation toStationDomain(KitchenStationEntity e) {
        return new KitchenStation(
                e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                e.getCode(), e.getName(), e.getStationType(), e.getPrinterDeviceId(),
                e.getDisplayOrder(), e.getExpectedPrepMinutes(), e.isExpoEnabled(),
                e.getPaperWidth(), e.getReceiptLanguage(), e.getReceiptEncoding(),
                e.isDefaultStation(), e.isActive(), e.getVersion()
        );
    }

    private static KitchenRoutingRule toRuleDomain(KitchenRoutingRuleEntity e) {
        return new KitchenRoutingRule(
                e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                e.getKitchenStationId(), e.getProductId(), e.getCategoryId(),
                e.getPrecedence(), e.getExpectedPrepMinutesOverride(), e.isActive(), e.getVersion()
        );
    }

    private static KitchenUnroutedItem toUnroutedDomain(KitchenUnroutedItemEntity e) {
        return new KitchenUnroutedItem(
                e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                e.getOutboxEventId(), e.getOrderId(), e.getOrderNumber(), e.getOrderItemId(),
                e.getProductId(), e.getProductNameSnapshot(), e.getQuantity(),
                e.getModifiersSummary(), e.getStatus(), e.getResolvedStationId(),
                e.getResolvedByUserId(), e.getResolvedAt(), e.getCreatedAt()
        );
    }

    private static KitchenPrintJob toPrintJobDomain(KitchenPrintJobEntity e) {
        return new KitchenPrintJob(
                e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                e.getKitchenTicketId(), e.getKitchenStationId(), e.getJobType(),
                e.getPrinterName(), e.getLanguage(), e.getPaperWidth(), e.getEncoding(),
                e.getRenderedText(), e.getEscPosBase64(), e.getStatus(), e.getAttemptCount(),
                e.getErrorMessage(), e.getIdempotencyKey(), e.getCreatedAt(), e.getLastAttemptAt()
        );
    }
}
