package com.restaurantpos.api.controller;

import com.restaurantpos.api.security.SecurityPrincipalResolver;
import com.restaurantpos.application.kitchen.KitchenConfigService;
import com.restaurantpos.application.kitchen.KitchenOutboxConsumerService;
import com.restaurantpos.application.kitchen.KitchenWorkflowService;
import com.restaurantpos.shared.dto.ApiResponse;
import com.restaurantpos.shared.dto.kitchen.ExpoOrderDto;
import com.restaurantpos.shared.dto.kitchen.KitchenDisplayConfigDto;
import com.restaurantpos.shared.dto.kitchen.KitchenPrintJobDto;
import com.restaurantpos.shared.dto.kitchen.KitchenPrinterConfigRequest;
import com.restaurantpos.shared.dto.kitchen.KitchenRoutingRuleDto;
import com.restaurantpos.shared.dto.kitchen.KitchenStationDto;
import com.restaurantpos.shared.dto.kitchen.KitchenTicketActionRequest;
import com.restaurantpos.shared.dto.kitchen.KitchenTicketDto;
import com.restaurantpos.shared.dto.kitchen.KitchenTicketEventDto;
import com.restaurantpos.shared.dto.kitchen.KitchenUnroutedItemDto;
import com.restaurantpos.shared.dto.kitchen.ResolveUnroutedItemRequest;
import com.restaurantpos.shared.dto.kitchen.SaveKitchenRoutingRuleRequest;
import com.restaurantpos.shared.dto.kitchen.SaveKitchenStationRequest;
import com.restaurantpos.shared.dto.kitchen.UpdateKitchenDisplayConfigRequest;
import com.restaurantpos.shared.dto.kitchen.UpdateKitchenItemStatusRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for Kitchen Display System (KDS), station routing, ticket lifecycle,
 * Expo order readiness aggregation, unrouted item resolution, and station printing.
 */
@RestController
@RequestMapping("/api/v1/kitchen")
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class KitchenController {

    private final KitchenConfigService configService;
    private final KitchenWorkflowService workflowService;
    private final KitchenOutboxConsumerService outboxConsumerService;

    public KitchenController(
            KitchenConfigService configService,
            KitchenWorkflowService workflowService,
            KitchenOutboxConsumerService outboxConsumerService
    ) {
        this.configService = configService;
        this.workflowService = workflowService;
        this.outboxConsumerService = outboxConsumerService;
    }

    @GetMapping("/stations")
    public ApiResponse<List<KitchenStationDto>> listStations() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(configService.listStations(actor), corr.value());
    }

    @PostMapping("/stations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KitchenStationDto> createStation(@Valid @RequestBody SaveKitchenStationRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(configService.createStation(actor, request, corr), corr.value());
    }

    @PutMapping("/stations/{stationId}")
    public ApiResponse<KitchenStationDto> updateStation(
            @PathVariable UUID stationId,
            @Valid @RequestBody SaveKitchenStationRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(configService.updateStation(actor, stationId, request, corr), corr.value());
    }

    @PutMapping("/stations/{stationId}/printer")
    public ApiResponse<KitchenStationDto> updateStationPrinter(
            @PathVariable UUID stationId,
            @Valid @RequestBody KitchenPrinterConfigRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(configService.updateStationPrinter(actor, stationId, request, corr), corr.value());
    }

    @GetMapping("/routing-rules")
    public ApiResponse<List<KitchenRoutingRuleDto>> listRoutingRules() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(configService.listRoutingRules(actor), corr.value());
    }

    @PostMapping("/routing-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KitchenRoutingRuleDto> createRoutingRule(@Valid @RequestBody SaveKitchenRoutingRuleRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(configService.createRoutingRule(actor, request, corr), corr.value());
    }

    @GetMapping("/display-config")
    public ApiResponse<KitchenDisplayConfigDto> getDisplayConfig() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(configService.getDisplayConfig(actor), corr.value());
    }

    @PutMapping("/display-config")
    public ApiResponse<KitchenDisplayConfigDto> updateDisplayConfig(
            @Valid @RequestBody UpdateKitchenDisplayConfigRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(configService.updateDisplayConfig(actor, request, corr), corr.value());
    }

    @GetMapping("/unrouted-items")
    public ApiResponse<List<KitchenUnroutedItemDto>> listOpenUnroutedItems() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(configService.listOpenUnroutedItems(actor), corr.value());
    }

    @PostMapping("/unrouted-items/{unroutedItemId}/resolve")
    public ApiResponse<KitchenTicketDto> resolveUnroutedItem(
            @PathVariable UUID unroutedItemId,
            @Valid @RequestBody ResolveUnroutedItemRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(configService.resolveUnroutedItem(actor, unroutedItemId, request, corr), corr.value());
    }

    @PostMapping("/outbox/process")
    public ApiResponse<Integer> processPendingOutbox() {
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(outboxConsumerService.processPendingEvents(25), corr.value());
    }

    @GetMapping("/stations/{stationId}/tickets")
    public ApiResponse<List<KitchenTicketDto>> listStationTickets(
            @PathVariable UUID stationId,
            @RequestParam(defaultValue = "FIFO") String sort
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        outboxConsumerService.processPendingEvents(25);
        return ApiResponse.ok(workflowService.listStationTickets(actor, stationId, sort), corr.value());
    }

    @GetMapping("/tickets/{ticketId}")
    public ApiResponse<KitchenTicketDto> getTicket(@PathVariable UUID ticketId) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(workflowService.getTicket(actor, ticketId), corr.value());
    }

    @GetMapping("/tickets/{ticketId}/events")
    public ApiResponse<List<KitchenTicketEventDto>> getTicketEvents(@PathVariable UUID ticketId) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(workflowService.getTicketEvents(actor, ticketId), corr.value());
    }

    @PostMapping("/tickets/{ticketId}/accept")
    public ApiResponse<KitchenTicketDto> acceptTicket(
            @PathVariable UUID ticketId,
            @Valid @RequestBody KitchenTicketActionRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(workflowService.acceptTicket(actor, ticketId, request, corr), corr.value());
    }

    @PostMapping("/tickets/{ticketId}/start")
    public ApiResponse<KitchenTicketDto> startTicket(
            @PathVariable UUID ticketId,
            @Valid @RequestBody KitchenTicketActionRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(workflowService.startTicket(actor, ticketId, request, corr), corr.value());
    }

    @PostMapping("/tickets/{ticketId}/items/{itemId}/status")
    public ApiResponse<KitchenTicketDto> updateItemStatus(
            @PathVariable UUID ticketId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateKitchenItemStatusRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(workflowService.updateItemStatus(actor, ticketId, itemId, request, corr), corr.value());
    }

    @PostMapping("/tickets/{ticketId}/ready")
    public ApiResponse<KitchenTicketDto> markTicketReady(
            @PathVariable UUID ticketId,
            @Valid @RequestBody KitchenTicketActionRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(workflowService.markTicketReady(actor, ticketId, request, corr), corr.value());
    }

    @PostMapping("/tickets/{ticketId}/serve")
    public ApiResponse<KitchenTicketDto> serveTicket(
            @PathVariable UUID ticketId,
            @Valid @RequestBody KitchenTicketActionRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(workflowService.serveTicket(actor, ticketId, request, corr), corr.value());
    }

    @PostMapping("/tickets/{ticketId}/hold")
    public ApiResponse<KitchenTicketDto> holdTicket(
            @PathVariable UUID ticketId,
            @Valid @RequestBody KitchenTicketActionRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(workflowService.holdTicket(actor, ticketId, request, corr), corr.value());
    }

    @PostMapping("/tickets/{ticketId}/release")
    public ApiResponse<KitchenTicketDto> releaseTicket(
            @PathVariable UUID ticketId,
            @Valid @RequestBody KitchenTicketActionRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(workflowService.releaseTicket(actor, ticketId, request, corr), corr.value());
    }

    @PostMapping("/tickets/{ticketId}/cancel")
    public ApiResponse<KitchenTicketDto> cancelTicket(
            @PathVariable UUID ticketId,
            @Valid @RequestBody KitchenTicketActionRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(workflowService.cancelTicket(actor, ticketId, request, corr), corr.value());
    }

    @PostMapping("/tickets/{ticketId}/refire")
    public ApiResponse<KitchenTicketDto> refireTicket(
            @PathVariable UUID ticketId,
            @Valid @RequestBody KitchenTicketActionRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(workflowService.refireTicket(actor, ticketId, request, corr), corr.value());
    }

    @GetMapping("/expo")
    public ApiResponse<List<ExpoOrderDto>> getExpoOrders() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        outboxConsumerService.processPendingEvents(25);
        return ApiResponse.ok(workflowService.getExpoOrders(actor), corr.value());
    }

    @PostMapping("/expo/{orderId}/serve")
    public ApiResponse<List<KitchenTicketDto>> serveExpoOrder(@PathVariable UUID orderId) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(workflowService.serveExpoOrder(actor, orderId, corr), corr.value());
    }

    @GetMapping("/print-jobs/failed")
    public ApiResponse<List<KitchenPrintJobDto>> listFailedPrintJobs() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(workflowService.listFailedPrintJobs(actor), corr.value());
    }

    @GetMapping("/tickets/{ticketId}/print-jobs")
    public ApiResponse<List<KitchenPrintJobDto>> listTicketPrintJobs(@PathVariable UUID ticketId) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(workflowService.listTicketPrintJobs(actor, ticketId), corr.value());
    }

    @PostMapping("/print-jobs/{jobId}/retry")
    public ApiResponse<KitchenPrintJobDto> retryPrintJob(@PathVariable UUID jobId) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(workflowService.retryPrintJob(actor, jobId, corr), corr.value());
    }
}
