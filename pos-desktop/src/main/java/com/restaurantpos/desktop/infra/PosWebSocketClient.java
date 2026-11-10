package com.restaurantpos.desktop.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.restaurantpos.shared.dto.pos.BranchRealtimeEventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Lightweight WebSocket subscriber receiving branch change notifications and deduplicating by sequence cursor.
 */
public final class PosWebSocketClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(PosWebSocketClient.class);

    private final URI wsUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AtomicLong lastSequence = new AtomicLong(0);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile WebSocket activeSocket;

    public PosWebSocketClient(String httpBaseUrl) {
        String wsUrl = httpBaseUrl.replaceFirst("^http://", "ws://").replaceFirst("^https://", "wss://");
        if (wsUrl.endsWith("/")) {
            wsUrl = wsUrl.substring(0, wsUrl.length() - 1);
        }
        this.wsUri = URI.create(wsUrl + "/ws/events");
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public void connect(String bearerToken, Consumer<BranchRealtimeEventDto> onEvent, Runnable onDisconnected) {
        if (closed.get() || bearerToken == null || bearerToken.isBlank()) {
            return;
        }
        httpClient.newWebSocketBuilder()
                .header("Authorization", "Bearer " + bearerToken)
                .buildAsync(wsUri, new WebSocket.Listener() {
                    private final StringBuilder buffer = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        activeSocket = webSocket;
                        LOG.info("Connected to branch WebSocket {}", wsUri);
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        buffer.append(data);
                        if (last) {
                            String payload = buffer.toString();
                            buffer.setLength(0);
                            handleMessage(payload, onEvent);
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        LOG.debug("WebSocket closed status={} reason={}", statusCode, reason);
                        if (!closed.get() && onDisconnected != null) {
                            onDisconnected.run();
                        }
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        LOG.debug("WebSocket error: {}", error.getMessage());
                        if (!closed.get() && onDisconnected != null) {
                            onDisconnected.run();
                        }
                    }
                });
    }

    private void handleMessage(String payload, Consumer<BranchRealtimeEventDto> onEvent) {
        try {
            BranchRealtimeEventDto event = objectMapper.readValue(payload, BranchRealtimeEventDto.class);
            long prev = lastSequence.get();
            if (event.sequenceNumber() <= prev) {
                return; // Deduplicate stale or redelivered event
            }
            lastSequence.compareAndSet(prev, event.sequenceNumber());
            onEvent.accept(event);
        } catch (Exception ex) {
            LOG.warn("Unable to parse WebSocket branch event", ex);
        }
    }

    public long lastSequence() {
        return lastSequence.get();
    }

    @Override
    public void close() {
        closed.set(true);
        WebSocket ws = activeSocket;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "Terminal shutdown");
            } catch (Exception ignored) {
            }
        }
    }
}
