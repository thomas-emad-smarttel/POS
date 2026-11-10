package com.restaurantpos.api.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantpos.domain.port.BranchEventPublishPort;
import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.shared.dto.pos.BranchRealtimeEventDto;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight, tenant-and-branch-isolated WebSocket notification hub with monotonic sequence numbers
 * and a bounded per-branch ring buffer for reconnect catch-up.
 */
@Component
public class WebSocketBranchEventHub extends TextWebSocketHandler implements BranchEventPublishPort {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketBranchEventHub.class);
    private static final int MAX_RECENT_EVENTS_PER_BRANCH = 200;
    public static final String PRINCIPAL_ATTR = "POS_PRINCIPAL";

    private final ObjectMapper objectMapper;
    private final SystemClockPort clock;
    private final Map<UUID, Set<WebSocketSession>> sessionsByBranch = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> sequencesByBranch = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<BranchRealtimeEventDto>> historyByBranch = new ConcurrentHashMap<>();

    public WebSocketBranchEventHub(ObjectMapper objectMapper, SystemClockPort clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Object attr = session.getAttributes().get(PRINCIPAL_ATTR);
        if (!(attr instanceof AccessPrincipal principal) || principal.activeBranchId() == null) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (Exception ignored) {
            }
            return;
        }
        sessionsByBranch.computeIfAbsent(principal.activeBranchId().value(), k -> ConcurrentHashMap.newKeySet())
                .add(session);
        LOG.debug("WebSocket terminal connected branchId={} user={}", principal.activeBranchId(), principal.username());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionsByBranch.values().forEach(set -> set.remove(session));
    }

    @Override
    public void publish(TenantId tenantId, BranchId branchId, String eventType, UUID entityId) {
        if (branchId == null) {
            return;
        }
        long seq = sequencesByBranch.computeIfAbsent(branchId.value(), k -> new AtomicLong(0)).incrementAndGet();
        BranchRealtimeEventDto event = new BranchRealtimeEventDto(
                seq, tenantId.value(), branchId.value(), eventType, entityId, clock.now()
        );
        recordHistory(branchId.value(), event);

        Set<WebSocketSession> sessions = sessionsByBranch.get(branchId.value());
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(event);
            TextMessage message = new TextMessage(json);
            for (WebSocketSession session : sessions) {
                if (!session.isOpen()) {
                    sessions.remove(session);
                    continue;
                }
                Object attr = session.getAttributes().get(PRINCIPAL_ATTR);
                if (attr instanceof AccessPrincipal p && tenantId.equals(p.tenantId())) {
                    synchronized (session) {
                        session.sendMessage(message);
                    }
                }
            }
        } catch (Exception ex) {
            LOG.warn("Failed to broadcast branch event seq={} type={}", seq, eventType, ex);
        }
    }

    public List<BranchRealtimeEventDto> eventsSince(TenantId tenantId, BranchId branchId, long sinceSequence) {
        Deque<BranchRealtimeEventDto> deque = historyByBranch.get(branchId.value());
        if (deque == null) {
            return List.of();
        }
        synchronized (deque) {
            List<BranchRealtimeEventDto> result = new ArrayList<>();
            for (BranchRealtimeEventDto ev : deque) {
                if (ev.sequenceNumber() > sinceSequence && ev.tenantId().equals(tenantId.value())) {
                    result.add(ev);
                }
            }
            return result;
        }
    }

    private void recordHistory(UUID branchId, BranchRealtimeEventDto event) {
        Deque<BranchRealtimeEventDto> deque = historyByBranch.computeIfAbsent(branchId, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(event);
            while (deque.size() > MAX_RECENT_EVENTS_PER_BRANCH) {
                deque.removeFirst();
            }
        }
    }
}
