package com.restaurantpos.desktop.infra;

import com.restaurantpos.shared.dto.ApiResponse;
import com.restaurantpos.shared.dto.ServerStatusDto;
import com.restaurantpos.shared.dto.kitchen.ExpoOrderDto;
import com.restaurantpos.shared.dto.kitchen.KitchenTicketActionRequest;
import com.restaurantpos.shared.dto.kitchen.KitchenTicketDto;
import com.restaurantpos.shared.dto.payment.CashSessionDto;
import com.restaurantpos.shared.dto.payment.OpenCashSessionRequest;
import com.restaurantpos.shared.dto.payment.OrderPaymentSummaryDto;
import com.restaurantpos.shared.dto.payment.ProcessPaymentRequest;
import com.restaurantpos.shared.dto.pos.BranchRealtimeEventDto;
import com.restaurantpos.shared.dto.pos.CreateOrderRequest;
import com.restaurantpos.shared.dto.pos.OrderDto;
import com.restaurantpos.shared.dto.security.LoginRequest;
import com.restaurantpos.shared.dto.security.LoginResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalServerClientTest {

    @Test
    void fetchesAndDeserializesHealthResponseFromLocalServer() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String json = """
                {
                  "success": true,
                  "data": {
                    "serviceName": "pos-local-server",
                    "status": "UP",
                    "version": "0.1.0-SNAPSHOT",
                    "operatingMode": "OFFLINE_CAPABLE",
                    "serverTime": "2026-11-10T12:00:00Z"
                  },
                  "error": null,
                  "correlationId": "corr-1",
                  "timestamp": "2026-11-10T12:00:00Z"
                }
                """;
        server.createContext("/api/v1/system/health", exchange -> {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            LocalServerClient client = new LocalServerClient("http://127.0.0.1:" + port);
            ApiResponse<ServerStatusDto> response = client.fetchHealth();

            assertTrue(response.success());
            assertEquals("pos-local-server", response.data().serviceName());
            assertEquals("UP", response.data().status());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void throwsApiClientExceptionOnStructuredUnauthorizedResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String errorJson = """
                {
                  "success": false,
                  "data": null,
                  "error": {
                    "code": "AUTH_INVALID_CREDENTIALS",
                    "message": "Invalid username or password",
                    "correlationId": "corr-2",
                    "timestamp": "2026-11-10T12:00:00Z",
                    "details": []
                  },
                  "correlationId": "corr-2",
                  "timestamp": "2026-11-10T12:00:00Z"
                }
                """;
        server.createContext("/api/v1/auth/login", exchange -> {
            byte[] bytes = errorJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(401, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            LocalServerClient client = new LocalServerClient("http://127.0.0.1:" + port);
            ApiClientException ex = assertThrows(ApiClientException.class,
                    () -> client.login(new LoginRequest("DEMO", "user1", "wrongpass", null, null)));
            assertEquals(401, ex.statusCode());
            assertEquals("AUTH_INVALID_CREDENTIALS", ex.errorCode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void deserializesLoginResponseWithProfileAndPermissions() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String okJson = """
                {
                  "success": true,
                  "data": {
                    "accessToken": "pos_sess_abc",
                    "tokenType": "Bearer",
                    "expiresAt": "2026-11-10T20:00:00Z",
                    "profile": {
                      "userId": "11111111-1111-1111-1111-111111111111",
                      "tenantId": "22222222-2222-2222-2222-222222222222",
                      "activeBranchId": "33333333-3333-3333-3333-333333333333",
                      "terminalId": null,
                      "sessionId": "44444444-4444-4444-4444-444444444444",
                      "username": "cashier1",
                      "fullName": "Cashier One",
                      "email": null,
                      "active": true,
                      "allowedBranchIds": ["33333333-3333-3333-3333-333333333333"],
                      "assignedRoles": ["CASHIER"],
                      "effectivePermissions": ["orders.create", "payments.process"],
                      "sessionExpiresAt": "2026-11-10T20:00:00Z"
                    }
                  },
                  "error": null,
                  "correlationId": "corr-3",
                  "timestamp": "2026-11-10T12:00:00Z"
                }
                """;
        server.createContext("/api/v1/auth/login", exchange -> {
            byte[] bytes = okJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            LocalServerClient client = new LocalServerClient("http://127.0.0.1:" + port);
            ApiResponse<LoginResponse> response = client.login(new LoginRequest("DEMO", "cashier1", "secret123", null, null));
            assertEquals("pos_sess_abc", response.data().accessToken());
            assertTrue(response.data().profile().effectivePermissions().contains("orders.create"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void deserializesOrderAndRecentEventsAndTranslatesConflictErrors() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String orderJson = """
                {
                  "success": true,
                  "data": {
                    "id": "%s",
                    "tenantId": "%s",
                    "branchId": "%s",
                    "terminalId": null,
                    "diningTableId": null,
                    "createdByUserId": "%s",
                    "assignedWaiterId": "%s",
                    "orderNumber": "ORD-1001",
                    "orderType": "TAKEAWAY",
                    "status": "OPEN",
                    "currencyCode": "USD",
                    "subtotalAmount": 25.00,
                    "discountAmount": 0.00,
                    "taxAmount": 3.75,
                    "totalAmount": 28.75,
                    "paidAmount": 0.00,
                    "guestCount": 1,
                    "customerReference": "Walk-in",
                    "notes": null,
                    "held": false,
                    "openedAt": "2026-11-10T12:00:00Z",
                    "sentToKitchenAt": null,
                    "closedAt": null,
                    "version": 1,
                    "items": [],
                    "discounts": [],
                    "taxes": []
                  },
                  "error": null,
                  "correlationId": "corr-4",
                  "timestamp": "2026-11-10T12:00:00Z"
                }
                """.formatted(orderId, tenantId, branchId, userId, userId);

        String eventsJson = """
                {
                  "success": true,
                  "data": [
                    {
                      "sequenceNumber": 42,
                      "tenantId": "%s",
                      "branchId": "%s",
                      "eventType": "ORDER_UPDATED",
                      "entityId": "%s",
                      "occurredAt": "2026-11-10T12:01:00Z"
                    }
                  ],
                  "error": null,
                  "correlationId": "corr-5",
                  "timestamp": "2026-11-10T12:01:00Z"
                }
                """.formatted(tenantId, branchId, orderId);

        String conflictJson = """
                {
                  "success": false,
                  "data": null,
                  "error": {
                    "code": "CONCURRENT_MODIFICATION",
                    "message": "Order was updated by another terminal",
                    "correlationId": "corr-6",
                    "timestamp": "2026-11-10T12:02:00Z",
                    "details": []
                  },
                  "correlationId": "corr-6",
                  "timestamp": "2026-11-10T12:02:00Z"
                }
                """;

        server.createContext("/api/v1/orders", exchange -> {
            byte[] bytes = orderJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.createContext("/api/v1/events/recent", exchange -> {
            byte[] bytes = eventsJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.createContext("/api/v1/orders/" + orderId + "/hold", exchange -> {
            byte[] bytes = conflictJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(409, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            LocalServerClient client = new LocalServerClient("http://127.0.0.1:" + port);

            ApiResponse<OrderDto> created = client.createOrder(
                    "token-1",
                    new CreateOrderRequest("TAKEAWAY", null, null, null, 1, "Walk-in", null)
            );
            assertEquals("ORD-1001", created.data().orderNumber());
            assertEquals(0, new BigDecimal("28.75").compareTo(created.data().totalAmount()));

            ApiResponse<List<BranchRealtimeEventDto>> events = client.fetchRecentEvents("token-1", 40L);
            assertEquals(1, events.data().size());
            assertEquals(42L, events.data().getFirst().sequenceNumber());

            ApiClientException conflict = assertThrows(ApiClientException.class,
                    () -> client.holdOrder("token-1", orderId, 0L));
            assertEquals(409, conflict.statusCode());
            assertEquals("CONCURRENT_MODIFICATION", conflict.errorCode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void deserializesPaymentSummaryAndCashSessionResponses() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        UUID orderId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID termId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID methodId = UUID.randomUUID();

        String paySummaryJson = """
                {
                  "success": true,
                  "data": {
                    "orderId": "%s",
                    "orderNumber": "ORD-2001",
                    "orderStatus": "PAID",
                    "currencyCode": "USD",
                    "orderTotal": 20.00,
                    "netPaidAmount": 20.00,
                    "totalRefundedAmount": 0.00,
                    "remainingDue": 0.00,
                    "payments": [],
                    "attempts": [],
                    "refunds": [],
                    "latestAttempt": null,
                    "receipt": null,
                    "hardwareNotice": null
                  },
                  "error": null,
                  "correlationId": "corr-p1",
                  "timestamp": "2026-11-10T15:00:00Z"
                }
                """.formatted(orderId);

        String cashSessionJson = """
                {
                  "success": true,
                  "data": {
                    "id": "%s",
                    "branchId": "%s",
                    "terminalId": "%s",
                    "openedByUserId": "%s",
                    "closedByUserId": null,
                    "reopenedByUserId": null,
                    "currencyCode": "USD",
                    "openingFloat": 100.00,
                    "derivedExpectedCash": 100.00,
                    "countedCashAmount": null,
                    "varianceAmount": null,
                    "status": "OPEN",
                    "openedAt": "2026-11-10T15:00:00Z",
                    "closedAt": null,
                    "reopenedAt": null,
                    "reopenReason": null,
                    "version": 0,
                    "movements": []
                  },
                  "error": null,
                  "correlationId": "corr-c1",
                  "timestamp": "2026-11-10T15:00:00Z"
                }
                """.formatted(sessionId, branchId, termId, userId);

        server.createContext("/api/v1/payments/process", exchange -> {
            byte[] bytes = paySummaryJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.createContext("/api/v1/cash-sessions/open", exchange -> {
            byte[] bytes = cashSessionJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            LocalServerClient client = new LocalServerClient("http://127.0.0.1:" + port);

            ApiResponse<OrderPaymentSummaryDto> summary = client.processPayment("t", new ProcessPaymentRequest(
                    orderId, methodId, "k1", new BigDecimal("20.00"), new BigDecimal("25.00"),
                    BigDecimal.ZERO, sessionId, null, null));
            assertEquals("PAID", summary.data().orderStatus());
            assertEquals(0, BigDecimal.ZERO.compareTo(summary.data().remainingDue()));

            ApiResponse<CashSessionDto> session = client.openCashSession("t",
                    new OpenCashSessionRequest(termId, new BigDecimal("100.00")));
            assertEquals("OPEN", session.data().status());
            assertEquals(0, new BigDecimal("100.00").compareTo(session.data().derivedExpectedCash()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void deserializesKdsStationTicketsExpoOrdersAndConflictErrors() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        UUID branchId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID stationId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        String ticketsJson = """
                {
                  "success": true,
                  "data": [{
                    "id": "%s",
                    "branchId": "%s",
                    "orderId": "%s",
                    "orderNumberSnapshot": "ORD-88",
                    "orderTypeSnapshot": "DINE_IN",
                    "tableLabelSnapshot": "Table 5",
                    "waiterId": null,
                    "waiterNameSnapshot": "Omar",
                    "kitchenStationId": "%s",
                    "stationCode": "HOT",
                    "stationName": "Hot Kitchen",
                    "ticketNumber": "KT-088",
                    "ticketKind": "INITIAL",
                    "status": "PREPARING",
                    "priorityLevel": "HIGH",
                    "priorityScore": 20,
                    "expectedPrepMinutes": 10,
                    "elapsedSeconds": 125,
                    "overdue": false,
                    "firedAt": "2026-11-10T15:00:00Z",
                    "acceptedAt": "2026-11-10T15:00:20Z",
                    "startedAt": "2026-11-10T15:00:40Z",
                    "readyAt": null,
                    "bumpedAt": null,
                    "cancelledAt": null,
                    "holdReason": null,
                    "cancelReason": null,
                    "version": 3,
                    "items": [{
                      "id": "%s",
                      "orderItemId": "%s",
                      "itemNameSnapshot": "Ribeye Steak",
                      "variantNameSnapshot": "300g",
                      "quantity": 1,
                      "selectedModifiers": ["Medium Rare"],
                      "prepInstructions": "Hot plate",
                      "notes": null,
                      "expectedPrepMinutes": 10,
                      "status": "PREPARING",
                      "startedAt": "2026-11-10T15:00:40Z",
                      "readyAt": null,
                      "servedAt": null,
                      "cancelReason": null,
                      "version": 1
                    }]
                  }],
                  "error": null,
                  "correlationId": "corr-k1",
                  "timestamp": "2026-11-10T15:02:05Z"
                }
                """.formatted(ticketId, branchId, orderId, stationId, itemId, UUID.randomUUID());

        String expoJson = """
                {
                  "success": true,
                  "data": [{
                    "orderId": "%s",
                    "orderNumber": "ORD-88",
                    "orderType": "DINE_IN",
                    "tableLabel": "Table 5",
                    "aggregateStatus": "IN_PROGRESS",
                    "allStationsReady": false,
                    "readyTicketCount": 0,
                    "totalTicketCount": 1,
                    "pendingStations": ["Hot Kitchen"],
                    "earliestFiredAt": "2026-11-10T15:00:00Z",
                    "maxElapsedSeconds": 125,
                    "anyOverdue": false,
                    "stationTickets": []
                  }],
                  "error": null,
                  "correlationId": "corr-e1",
                  "timestamp": "2026-11-10T15:02:05Z"
                }
                """.formatted(orderId);

        String conflictJson = """
                {
                  "success": false,
                  "data": null,
                  "error": {
                    "code": "OPTIMISTIC_LOCK_CONFLICT",
                    "message": "Ticket was modified by another screen",
                    "correlationId": "corr-c9",
                    "timestamp": "2026-11-10T15:02:10Z",
                    "details": []
                  },
                  "correlationId": "corr-c9",
                  "timestamp": "2026-11-10T15:02:10Z"
                }
                """;

        server.createContext("/api/v1/kitchen/stations/" + stationId + "/tickets", exchange -> {
            byte[] bytes = ticketsJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.createContext("/api/v1/kitchen/expo", exchange -> {
            byte[] bytes = expoJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.createContext("/api/v1/kitchen/tickets/" + ticketId + "/ready", exchange -> {
            byte[] bytes = conflictJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(409, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            LocalServerClient client = new LocalServerClient("http://127.0.0.1:" + port);

            ApiResponse<List<KitchenTicketDto>> tickets = client.listStationTickets("t", stationId, "FIFO");
            assertEquals(1, tickets.data().size());
            assertEquals("KT-088", tickets.data().get(0).ticketNumber());
            assertEquals(3L, tickets.data().get(0).version());

            ApiResponse<List<ExpoOrderDto>> expo = client.getExpoOrders("t");
            assertEquals(1, expo.data().size());
            assertEquals("ORD-88", expo.data().get(0).orderNumber());
            assertEquals(List.of("Hot Kitchen"), expo.data().get(0).pendingStations());

            ApiClientException conflict = assertThrows(ApiClientException.class, () ->
                    client.markKitchenTicketReady("t", ticketId, new KitchenTicketActionRequest(2L, null, null)));
            assertEquals(409, conflict.statusCode());
            assertEquals("OPTIMISTIC_LOCK_CONFLICT", conflict.errorCode());
        } finally {
            server.stop(0);
        }
    }
}
