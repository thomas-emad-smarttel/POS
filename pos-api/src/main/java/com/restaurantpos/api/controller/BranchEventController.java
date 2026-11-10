package com.restaurantpos.api.controller;

import com.restaurantpos.api.security.SecurityPrincipalResolver;
import com.restaurantpos.api.websocket.WebSocketBranchEventHub;
import com.restaurantpos.shared.dto.ApiResponse;
import com.restaurantpos.shared.dto.pos.BranchRealtimeEventDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catch-up endpoint allowing reconnected terminals to recover missed branch events since a sequence cursor.
 */
@RestController
@RequestMapping("/api/v1/events")
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class BranchEventController {

    private final WebSocketBranchEventHub eventHub;

    public BranchEventController(WebSocketBranchEventHub eventHub) {
        this.eventHub = eventHub;
    }

    @GetMapping("/recent")
    public ApiResponse<List<BranchRealtimeEventDto>> recentEvents(
            @RequestParam(defaultValue = "0") long sinceSequence
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        if (actor.activeBranchId() == null) {
            return ApiResponse.ok(List.of(), corr.value());
        }
        return ApiResponse.ok(
                eventHub.eventsSince(actor.tenantId(), actor.activeBranchId(), sinceSequence),
                corr.value()
        );
    }
}
