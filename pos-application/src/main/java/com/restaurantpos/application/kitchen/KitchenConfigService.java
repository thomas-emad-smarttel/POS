package com.restaurantpos.application.kitchen;

import com.restaurantpos.application.audit.AuditRecordingService;
import com.restaurantpos.application.security.PermissionPolicy;
import com.restaurantpos.domain.exception.DomainException;
import com.restaurantpos.domain.exception.ResourceNotFoundException;
import com.restaurantpos.domain.exception.UnauthorizedOperationException;
import com.restaurantpos.domain.kitchen.KitchenDisplayConfiguration;
import com.restaurantpos.domain.kitchen.KitchenPriorityLevel;
import com.restaurantpos.domain.kitchen.KitchenRoutingRule;
import com.restaurantpos.domain.kitchen.KitchenStation;
import com.restaurantpos.domain.kitchen.KitchenTicket;
import com.restaurantpos.domain.kitchen.KitchenTicketEvent;
import com.restaurantpos.domain.kitchen.KitchenTicketItem;
import com.restaurantpos.domain.kitchen.KitchenTicketItemStatus;
import com.restaurantpos.domain.kitchen.KitchenTicketStatus;
import com.restaurantpos.domain.kitchen.KitchenUnroutedItem;
import com.restaurantpos.domain.menu.Product;
import com.restaurantpos.domain.port.BranchEventPublishPort;
import com.restaurantpos.domain.port.KitchenRepositoryPort;
import com.restaurantpos.domain.port.MenuRepositoryPort;
import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.shared.dto.kitchen.KitchenDisplayConfigDto;
import com.restaurantpos.shared.dto.kitchen.KitchenPrinterConfigRequest;
import com.restaurantpos.shared.dto.kitchen.KitchenRoutingRuleDto;
import com.restaurantpos.shared.dto.kitchen.KitchenStationDto;
import com.restaurantpos.shared.dto.kitchen.KitchenTicketDto;
import com.restaurantpos.shared.dto.kitchen.KitchenUnroutedItemDto;
import com.restaurantpos.shared.dto.kitchen.ResolveUnroutedItemRequest;
import com.restaurantpos.shared.dto.kitchen.SaveKitchenRoutingRuleRequest;
import com.restaurantpos.shared.dto.kitchen.SaveKitchenStationRequest;
import com.restaurantpos.shared.dto.kitchen.UpdateKitchenDisplayConfigRequest;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.security.AuditAction;
import com.restaurantpos.shared.security.PermissionCode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Configures branch kitchen stations, printer settings, routing rules, Expo display config,
 * and authorized resolution of unrouted kitchen items.
 */
public class KitchenConfigService {

    private final KitchenRepositoryPort kitchenRepo;
    private final MenuRepositoryPort menuRepo;
    private final KitchenPrintDispatcher printDispatcher;
    private final BranchEventPublishPort eventPublisher;
    private final AuditRecordingService auditService;
    private final SystemClockPort clock;

    public KitchenConfigService(
            KitchenRepositoryPort kitchenRepo,
            MenuRepositoryPort menuRepo,
            KitchenPrintDispatcher printDispatcher,
            BranchEventPublishPort eventPublisher,
            AuditRecordingService auditService,
            SystemClockPort clock
    ) {
        this.kitchenRepo = kitchenRepo;
        this.menuRepo = menuRepo;
        this.printDispatcher = printDispatcher;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.clock = clock;
    }

    public List<KitchenStationDto> listStations(AccessPrincipal actor) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.KITCHEN_VIEW);
        return kitchenRepo.findStationsByBranch(actor.tenantId(), branchId).stream()
                .map(KitchenConfigService::toStationDto)
                .toList();
    }

    public KitchenStationDto createStation(AccessPrincipal actor, SaveKitchenStationRequest req, CorrelationId corr) {
        BranchId branchId = requireStationManage(actor);
        KitchenStation saved = kitchenRepo.saveStation(new KitchenStation(
                UUID.randomUUID(), actor.tenantId(), branchId,
                req.code().trim(), req.name().trim(), req.stationType().trim().toUpperCase(),
                req.printerDeviceId(), req.displayOrder(), req.expectedPrepMinutes(), req.expoEnabled(),
                req.paperWidth() != null ? req.paperWidth() : 42,
                req.receiptLanguage() != null ? req.receiptLanguage() : "BILINGUAL",
                req.receiptEncoding() != null ? req.receiptEncoding() : "UTF-8",
                req.defaultStation(), req.active(), 0L
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.KITCHEN_STATION_SAVED,
                "KitchenStation", saved.id().toString(), "{\"code\":\"" + saved.code() + "\"}", corr);
        return toStationDto(saved);
    }

    public KitchenStationDto updateStation(
            AccessPrincipal actor,
            UUID stationId,
            SaveKitchenStationRequest req,
            CorrelationId corr
    ) {
        BranchId branchId = requireStationManage(actor);
        KitchenStation existing = kitchenRepo.findStationScoped(stationId, actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Kitchen station not found in branch"));
        KitchenStation saved = kitchenRepo.saveStation(new KitchenStation(
                existing.id(), existing.tenantId(), existing.branchId(),
                req.code().trim(), req.name().trim(), req.stationType().trim().toUpperCase(),
                req.printerDeviceId(), req.displayOrder(), req.expectedPrepMinutes(), req.expoEnabled(),
                req.paperWidth() != null ? req.paperWidth() : existing.paperWidth(),
                req.receiptLanguage() != null ? req.receiptLanguage() : existing.receiptLanguage(),
                req.receiptEncoding() != null ? req.receiptEncoding() : existing.receiptEncoding(),
                req.defaultStation(), req.active(), existing.version()
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.KITCHEN_STATION_SAVED,
                "KitchenStation", saved.id().toString(), "{\"updated\":true}", corr);
        return toStationDto(saved);
    }

    public KitchenStationDto updateStationPrinter(
            AccessPrincipal actor,
            UUID stationId,
            KitchenPrinterConfigRequest req,
            CorrelationId corr
    ) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.KITCHEN_PRINTER_MANAGE);
        KitchenStation existing = kitchenRepo.findStationScoped(stationId, actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Kitchen station not found in branch"));
        KitchenStation saved = kitchenRepo.saveStation(new KitchenStation(
                existing.id(), existing.tenantId(), existing.branchId(),
                existing.code(), existing.name(), existing.stationType(),
                req.printerDeviceId(), existing.displayOrder(), existing.expectedPrepMinutes(), existing.expoEnabled(),
                req.paperWidth(),
                req.receiptLanguage() != null ? req.receiptLanguage() : existing.receiptLanguage(),
                req.receiptEncoding() != null ? req.receiptEncoding() : existing.receiptEncoding(),
                existing.defaultStation(), existing.active(), existing.version()
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.SETTINGS_CHANGED,
                "KitchenStation", saved.id().toString(), "{\"printerConfigured\":true}", corr);
        return toStationDto(saved);
    }

    public List<KitchenRoutingRuleDto> listRoutingRules(AccessPrincipal actor) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.KITCHEN_VIEW);
        Map<UUID, KitchenStation> stations = kitchenRepo.findStationsByBranch(actor.tenantId(), branchId).stream()
                .collect(Collectors.toMap(KitchenStation::id, Function.identity()));
        return kitchenRepo.findRoutingRulesByBranch(actor.tenantId(), branchId).stream()
                .map(r -> toRuleDto(r, stations.get(r.kitchenStationId())))
                .toList();
    }

    public KitchenRoutingRuleDto createRoutingRule(AccessPrincipal actor, SaveKitchenRoutingRuleRequest req, CorrelationId corr) {
        BranchId branchId = requireRoutingManage(actor);
        KitchenStation station = kitchenRepo.findStationScoped(req.kitchenStationId(), actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Target kitchen station not found in branch"));
        if (req.productId() != null) {
            menuRepo.findProductScoped(req.productId(), actor.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        }
        if (req.categoryId() != null) {
            menuRepo.findCategoryScoped(req.categoryId(), actor.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        }

        KitchenRoutingRule saved = kitchenRepo.saveRoutingRule(new KitchenRoutingRule(
                UUID.randomUUID(), actor.tenantId(), branchId, station.id(),
                req.productId(), req.categoryId(), req.precedence(),
                req.expectedPrepMinutesOverride(), req.active(), 0L
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.KITCHEN_ROUTING_RULE_SAVED,
                "KitchenRoutingRule", saved.id().toString(), "{\"scope\":\"" + saved.scopeName() + "\"}", corr);
        return toRuleDto(saved, station);
    }

    public KitchenDisplayConfigDto getDisplayConfig(AccessPrincipal actor) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.KITCHEN_VIEW);
        return kitchenRepo.findDisplayConfig(actor.tenantId(), branchId)
                .map(c -> new KitchenDisplayConfigDto(c.expoEnabled(), c.defaultSortMode(),
                        c.defaultExpectedPrepMinutes(), c.autoPrintOnDispatch()))
                .orElse(new KitchenDisplayConfigDto(true, "FIFO", 10, true));
    }

    public KitchenDisplayConfigDto updateDisplayConfig(
            AccessPrincipal actor,
            UpdateKitchenDisplayConfigRequest req,
            CorrelationId corr
    ) {
        BranchId branchId = requireStationManage(actor);
        KitchenDisplayConfiguration existing = kitchenRepo.findDisplayConfig(actor.tenantId(), branchId).orElse(null);
        KitchenDisplayConfiguration saved = kitchenRepo.saveDisplayConfig(new KitchenDisplayConfiguration(
                existing != null ? existing.id() : UUID.randomUUID(),
                actor.tenantId(),
                branchId,
                req.expoEnabled(),
                req.defaultSortMode(),
                req.defaultExpectedPrepMinutes(),
                req.autoPrintOnDispatch(),
                existing != null ? existing.version() : 0L
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.SETTINGS_CHANGED,
                "KitchenDisplayConfig", saved.id().toString(), "{\"expoEnabled\":" + saved.expoEnabled() + "}", corr);
        return new KitchenDisplayConfigDto(saved.expoEnabled(), saved.defaultSortMode(),
                saved.defaultExpectedPrepMinutes(), saved.autoPrintOnDispatch());
    }

    public List<KitchenUnroutedItemDto> listOpenUnroutedItems(AccessPrincipal actor) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.KITCHEN_VIEW);
        return kitchenRepo.findOpenUnroutedItems(actor.tenantId(), branchId).stream()
                .map(u -> new KitchenUnroutedItemDto(
                        u.id(), u.outboxEventId(), u.orderId(), u.orderNumber(), u.orderItemId(),
                        u.productId(), u.productNameSnapshot(), u.quantity(), u.modifiersSummary(),
                        u.status(), u.resolvedStationId(), u.createdAt()
                ))
                .toList();
    }

    public KitchenTicketDto resolveUnroutedItem(
            AccessPrincipal actor,
            UUID unroutedItemId,
            ResolveUnroutedItemRequest req,
            CorrelationId corr
    ) {
        BranchId branchId = requireRoutingManage(actor);
        KitchenUnroutedItem unrouted = kitchenRepo.findUnroutedItemScoped(unroutedItemId, actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Unrouted kitchen item not found"));
        if (!"OPEN".equals(unrouted.status())) {
            throw new DomainException("UNROUTED_ALREADY_RESOLVED", "Unrouted item has already been resolved");
        }
        KitchenStation station = kitchenRepo.findStationScoped(req.kitchenStationId(), actor.tenantId(), branchId)
                .filter(KitchenStation::active)
                .orElseThrow(() -> new ResourceNotFoundException("Active target kitchen station not found in branch"));

        if (req.saveCategoryOrProductRule() && unrouted.productId() != null) {
            Product prod = menuRepo.findProductScoped(unrouted.productId(), actor.tenantId()).orElse(null);
            kitchenRepo.saveRoutingRule(new KitchenRoutingRule(
                    UUID.randomUUID(), actor.tenantId(), branchId, station.id(),
                    prod != null ? prod.id() : unrouted.productId(), null, 10, null, true, 0L
            ));
        }

        Instant now = clock.now();
        UUID ticketId = UUID.randomUUID();
        String ticketNumber = "K-" + Long.toString(now.toEpochMilli(), 36).toUpperCase()
                + "-" + UUID.randomUUID().toString().substring(0, 3).toUpperCase();
        List<String> mods = (unrouted.modifiersSummary() == null || unrouted.modifiersSummary().isBlank())
                ? List.of() : List.of(unrouted.modifiersSummary());
        KitchenTicketItem item = new KitchenTicketItem(
                UUID.randomUUID(), actor.tenantId(), branchId, ticketId, unrouted.orderItemId(),
                unrouted.productNameSnapshot(), null, unrouted.quantity(), mods,
                null, null, station.expectedPrepMinutes(), KitchenTicketItemStatus.NEW,
                null, null, null, null, 0L
        );

        KitchenTicket saved = kitchenRepo.saveTicket(new KitchenTicket(
                ticketId, actor.tenantId(), branchId, unrouted.orderId(), unrouted.orderNumber(),
                "MANUAL_ROUTE", null, actor.userId(), actor.username(), station.id(), unrouted.outboxEventId(),
                ticketNumber, "SUPPLEMENTAL", KitchenTicketStatus.NEW, KitchenPriorityLevel.NORMAL,
                0, station.expectedPrepMinutes(), now, null, null, null, null, null, null,
                null, null, 0L, List.of(item)
        ));

        kitchenRepo.appendTicketEvent(new KitchenTicketEvent(
                UUID.randomUUID(), actor.tenantId(), branchId, saved.id(), item.id(), actor.userId(),
                "FIRED", null, "NEW", "Manually routed from unrouted queue", null, now
        ));
        kitchenRepo.saveUnroutedItem(new KitchenUnroutedItem(
                unrouted.id(), unrouted.tenantId(), unrouted.branchId(), unrouted.outboxEventId(),
                unrouted.orderId(), unrouted.orderNumber(), unrouted.orderItemId(), unrouted.productId(),
                unrouted.productNameSnapshot(), unrouted.quantity(), unrouted.modifiersSummary(),
                "RESOLVED", station.id(), actor.userId(), now, unrouted.createdAt()
        ));

        printDispatcher.dispatchTicketPrint(saved, station, "SUPPLEMENTAL", "Manual route", actor.userId(), corr);
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.KITCHEN_UNROUTED_RESOLVED,
                "KitchenUnroutedItem", unrouted.id().toString(), "{\"stationCode\":\"" + station.code() + "\"}", corr);
        eventPublisher.publish(actor.tenantId(), branchId, "KITCHEN_TICKET_CREATED", saved.id());
        return KitchenWorkflowService.toTicketDto(saved, station, now);
    }

    public static KitchenStationDto toStationDto(KitchenStation s) {
        return new KitchenStationDto(
                s.id(), s.branchId().value(), s.code(), s.name(), s.stationType(),
                s.printerDeviceId(), s.displayOrder(), s.expectedPrepMinutes(), s.expoEnabled(),
                s.paperWidth(), s.receiptLanguage(), s.receiptEncoding(), s.defaultStation(),
                s.active(), s.version()
        );
    }

    private static KitchenRoutingRuleDto toRuleDto(KitchenRoutingRule r, KitchenStation s) {
        return new KitchenRoutingRuleDto(
                r.id(), r.branchId().value(), r.kitchenStationId(),
                s != null ? s.code() : "UNKNOWN", s != null ? s.name() : "Unknown Station",
                r.productId(), r.categoryId(), r.scopeName(), r.precedence(),
                r.expectedPrepMinutesOverride(), r.active(), r.version()
        );
    }

    private static BranchId requireBranchPermission(AccessPrincipal actor, PermissionCode perm) {
        if (actor == null || actor.activeBranchId() == null) {
            throw new UnauthorizedOperationException(perm, "Active branch session required");
        }
        PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), perm);
        return actor.activeBranchId();
    }

    private static BranchId requireStationManage(AccessPrincipal actor) {
        if (actor == null || actor.activeBranchId() == null) {
            throw new UnauthorizedOperationException(PermissionCode.KITCHEN_STATION_MANAGE, "Active branch required");
        }
        if (actor.hasPermission(PermissionCode.KITCHEN_STATION_MANAGE)) {
            PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), PermissionCode.KITCHEN_STATION_MANAGE);
        } else {
            PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), PermissionCode.KITCHEN_MANAGE);
        }
        return actor.activeBranchId();
    }

    private static BranchId requireRoutingManage(AccessPrincipal actor) {
        if (actor == null || actor.activeBranchId() == null) {
            throw new UnauthorizedOperationException(PermissionCode.KITCHEN_ROUTING_MANAGE, "Active branch required");
        }
        if (actor.hasPermission(PermissionCode.KITCHEN_ROUTING_MANAGE)) {
            PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), PermissionCode.KITCHEN_ROUTING_MANAGE);
        } else {
            PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), PermissionCode.KITCHEN_MANAGE);
        }
        return actor.activeBranchId();
    }
}
