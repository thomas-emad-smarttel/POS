package com.restaurantpos.desktop.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.restaurantpos.shared.dto.ApiResponse;
import com.restaurantpos.shared.dto.ServerStatusDto;
import com.restaurantpos.shared.dto.kitchen.ExpoOrderDto;
import com.restaurantpos.shared.dto.kitchen.KitchenPrintJobDto;
import com.restaurantpos.shared.dto.kitchen.KitchenRoutingRuleDto;
import com.restaurantpos.shared.dto.kitchen.KitchenStationDto;
import com.restaurantpos.shared.dto.kitchen.KitchenTicketActionRequest;
import com.restaurantpos.shared.dto.kitchen.KitchenTicketDto;
import com.restaurantpos.shared.dto.kitchen.KitchenUnroutedItemDto;
import com.restaurantpos.shared.dto.kitchen.ResolveUnroutedItemRequest;
import com.restaurantpos.shared.dto.kitchen.SaveKitchenRoutingRuleRequest;
import com.restaurantpos.shared.dto.kitchen.SaveKitchenStationRequest;
import com.restaurantpos.shared.dto.kitchen.UpdateKitchenItemStatusRequest;
import com.restaurantpos.shared.dto.payment.CashSessionDto;
import com.restaurantpos.shared.dto.payment.CloseCashSessionRequest;
import com.restaurantpos.shared.dto.payment.DrawerEventDto;
import com.restaurantpos.shared.dto.payment.GenerateReceiptRequest;
import com.restaurantpos.shared.dto.payment.OpenCashSessionRequest;
import com.restaurantpos.shared.dto.payment.OpenDrawerRequest;
import com.restaurantpos.shared.dto.payment.OrderPaymentSummaryDto;
import com.restaurantpos.shared.dto.payment.PaymentMethodDto;
import com.restaurantpos.shared.dto.payment.ProcessPaymentRequest;
import com.restaurantpos.shared.dto.payment.ReceiptDto;
import com.restaurantpos.shared.dto.payment.ReconciliationReportDto;
import com.restaurantpos.shared.dto.payment.RecordCashMovementRequest;
import com.restaurantpos.shared.dto.payment.RefundRequest;
import com.restaurantpos.shared.dto.payment.ReopenCashSessionRequest;
import com.restaurantpos.shared.dto.pos.AddOrderItemRequest;
import com.restaurantpos.shared.dto.pos.ApplyDiscountRequest;
import com.restaurantpos.shared.dto.pos.BranchProductOverrideRequest;
import com.restaurantpos.shared.dto.pos.BranchRealtimeEventDto;
import com.restaurantpos.shared.dto.pos.CancelOrderRequest;
import com.restaurantpos.shared.dto.pos.CategoryDto;
import com.restaurantpos.shared.dto.pos.ChangeOrderTypeRequest;
import com.restaurantpos.shared.dto.pos.CreateOrderRequest;
import com.restaurantpos.shared.dto.pos.DiningAreaDto;
import com.restaurantpos.shared.dto.pos.DiningTableDto;
import com.restaurantpos.shared.dto.pos.OrderDto;
import com.restaurantpos.shared.dto.pos.OrderSummaryDto;
import com.restaurantpos.shared.dto.pos.ProductDto;
import com.restaurantpos.shared.dto.pos.SaveCategoryRequest;
import com.restaurantpos.shared.dto.pos.SaveDiningAreaRequest;
import com.restaurantpos.shared.dto.pos.SaveDiningTableRequest;
import com.restaurantpos.shared.dto.pos.SaveProductRequest;
import com.restaurantpos.shared.dto.pos.TransferOrderRequest;
import com.restaurantpos.shared.dto.pos.UpdateOrderItemRequest;
import com.restaurantpos.shared.dto.pos.UpdateTableStatusRequest;
import com.restaurantpos.shared.dto.security.AssignUserBranchesRequest;
import com.restaurantpos.shared.dto.security.AssignUserRolesRequest;
import com.restaurantpos.shared.dto.security.AuditLogDto;
import com.restaurantpos.shared.dto.security.BranchSummaryDto;
import com.restaurantpos.shared.dto.security.ChangePasswordRequest;
import com.restaurantpos.shared.dto.security.CreateRoleRequest;
import com.restaurantpos.shared.dto.security.CreateUserRequest;
import com.restaurantpos.shared.dto.security.LoginRequest;
import com.restaurantpos.shared.dto.security.LoginResponse;
import com.restaurantpos.shared.dto.security.PermissionDto;
import com.restaurantpos.shared.dto.security.RoleDto;
import com.restaurantpos.shared.dto.security.SessionDto;
import com.restaurantpos.shared.dto.security.SupervisorApprovalRequest;
import com.restaurantpos.shared.dto.security.SupervisorApprovalResponse;
import com.restaurantpos.shared.dto.security.SwitchBranchRequest;
import com.restaurantpos.shared.dto.security.UpdateRolePermissionsRequest;
import com.restaurantpos.shared.dto.security.UpdateRoleRequest;
import com.restaurantpos.shared.dto.security.UpdateUserRequest;
import com.restaurantpos.shared.dto.security.UpdateUserStatusRequest;
import com.restaurantpos.shared.dto.security.UserProfileDto;
import com.restaurantpos.shared.dto.security.UserSummaryDto;
import com.restaurantpos.shared.model.CorrelationId;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Lightweight HTTP client communicating exclusively with the branch Local Server API.
 */
public class LocalServerClient {

    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LocalServerClient(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build());
    }

    public LocalServerClient(String baseUrl, HttpClient httpClient) {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public ApiResponse<ServerStatusDto> fetchHealth() throws IOException, InterruptedException {
        return send("GET", "/api/v1/system/health", null, null, new TypeReference<>() {});
    }

    public ApiResponse<LoginResponse> login(LoginRequest request) throws IOException, InterruptedException {
        return send("POST", "/api/v1/auth/login", null, request, new TypeReference<>() {});
    }

    public void logout(String token) throws IOException, InterruptedException {
        send("POST", "/api/v1/auth/logout", token, null, new TypeReference<ApiResponse<Void>>() {});
    }

    public ApiResponse<UserProfileDto> fetchProfile(String token) throws IOException, InterruptedException {
        return send("GET", "/api/v1/auth/me", token, null, new TypeReference<>() {});
    }

    public ApiResponse<UserProfileDto> switchBranch(String token, UUID branchId) throws IOException, InterruptedException {
        return send("POST", "/api/v1/auth/switch-branch", token, new SwitchBranchRequest(branchId), new TypeReference<>() {});
    }

    public void changePassword(String token, ChangePasswordRequest request) throws IOException, InterruptedException {
        send("POST", "/api/v1/auth/change-password", token, request, new TypeReference<ApiResponse<Void>>() {});
    }

    public ApiResponse<List<UserSummaryDto>> listUsers(String token, int page, int size) throws IOException, InterruptedException {
        return send("GET", "/api/v1/users?page=" + page + "&size=" + size, token, null, new TypeReference<>() {});
    }

    public ApiResponse<UserSummaryDto> createUser(String token, CreateUserRequest request) throws IOException, InterruptedException {
        return send("POST", "/api/v1/users", token, request, new TypeReference<>() {});
    }

    public ApiResponse<UserSummaryDto> updateUser(String token, UUID userId, UpdateUserRequest request) throws IOException, InterruptedException {
        return send("PATCH", "/api/v1/users/" + userId, token, request, new TypeReference<>() {});
    }

    public ApiResponse<UserSummaryDto> setUserStatus(String token, UUID userId, boolean active) throws IOException, InterruptedException {
        return send("POST", "/api/v1/users/" + userId + "/status", token, new UpdateUserStatusRequest(active), new TypeReference<>() {});
    }

    public ApiResponse<UserSummaryDto> assignUserBranches(String token, UUID userId, List<UUID> branchIds) throws IOException, InterruptedException {
        return send("PUT", "/api/v1/users/" + userId + "/branches", token, new AssignUserBranchesRequest(branchIds), new TypeReference<>() {});
    }

    public ApiResponse<UserSummaryDto> assignUserRoles(String token, UUID userId, AssignUserRolesRequest request) throws IOException, InterruptedException {
        return send("PUT", "/api/v1/users/" + userId + "/roles", token, request, new TypeReference<>() {});
    }

    public ApiResponse<List<BranchSummaryDto>> listBranches(String token) throws IOException, InterruptedException {
        return send("GET", "/api/v1/branches", token, null, new TypeReference<>() {});
    }

    public ApiResponse<List<RoleDto>> listRoles(String token) throws IOException, InterruptedException {
        return send("GET", "/api/v1/roles?page=0&size=100", token, null, new TypeReference<>() {});
    }

    public ApiResponse<RoleDto> createRole(String token, CreateRoleRequest request) throws IOException, InterruptedException {
        return send("POST", "/api/v1/roles", token, request, new TypeReference<>() {});
    }

    public ApiResponse<RoleDto> updateRole(String token, UUID roleId, UpdateRoleRequest request) throws IOException, InterruptedException {
        return send("PUT", "/api/v1/roles/" + roleId, token, request, new TypeReference<>() {});
    }

    public ApiResponse<RoleDto> updateRolePermissions(String token, UUID roleId, UpdateRolePermissionsRequest request)
            throws IOException, InterruptedException {
        return send("PUT", "/api/v1/roles/" + roleId + "/permissions", token, request, new TypeReference<>() {});
    }

    public ApiResponse<List<PermissionDto>> listPermissions(String token) throws IOException, InterruptedException {
        return send("GET", "/api/v1/permissions", token, null, new TypeReference<>() {});
    }

    public ApiResponse<List<SessionDto>> listSessions(String token, int page, int size) throws IOException, InterruptedException {
        return send("GET", "/api/v1/sessions?page=" + page + "&size=" + size, token, null, new TypeReference<>() {});
    }

    public void revokeSession(String token, UUID sessionId) throws IOException, InterruptedException {
        send("POST", "/api/v1/sessions/" + sessionId + "/revoke", token, null, new TypeReference<ApiResponse<Void>>() {});
    }

    public ApiResponse<List<AuditLogDto>> listAuditLogs(String token, UUID branchId, int page, int size)
            throws IOException, InterruptedException {
        String path = "/api/v1/audit-logs?page=" + page + "&size=" + size + (branchId != null ? "&branchId=" + branchId : "");
        return send("GET", path, token, null, new TypeReference<>() {});
    }

    // --- Phase 4: Menu, Tables, Orders & Events ---

    public ApiResponse<List<CategoryDto>> listMenuCategories(String token) throws IOException, InterruptedException {
        return send("GET", "/api/v1/menu/categories", token, null, new TypeReference<>() {});
    }

    public ApiResponse<CategoryDto> createCategory(String token, SaveCategoryRequest request) throws IOException, InterruptedException {
        return send("POST", "/api/v1/menu/categories", token, request, new TypeReference<>() {});
    }

    public ApiResponse<List<ProductDto>> searchMenuProducts(String token, UUID categoryId, String query, int page, int size)
            throws IOException, InterruptedException {
        StringBuilder sb = new StringBuilder("/api/v1/menu/products?page=").append(page).append("&size=").append(size);
        if (categoryId != null) {
            sb.append("&categoryId=").append(categoryId);
        }
        if (query != null && !query.isBlank()) {
            sb.append("&query=").append(java.net.URLEncoder.encode(query.trim(), java.nio.charset.StandardCharsets.UTF_8));
        }
        return send("GET", sb.toString(), token, null, new TypeReference<>() {});
    }

    public ApiResponse<ProductDto> findProductByBarcode(String token, String barcode) throws IOException, InterruptedException {
        String encoded = java.net.URLEncoder.encode(barcode.trim(), java.nio.charset.StandardCharsets.UTF_8);
        return send("GET", "/api/v1/menu/products/by-barcode/" + encoded, token, null, new TypeReference<>() {});
    }

    public ApiResponse<ProductDto> createProduct(String token, SaveProductRequest request) throws IOException, InterruptedException {
        return send("POST", "/api/v1/menu/products", token, request, new TypeReference<>() {});
    }

    public void setBranchProductOverride(String token, UUID productId, BranchProductOverrideRequest request)
            throws IOException, InterruptedException {
        send("PUT", "/api/v1/menu/products/" + productId + "/branch-override", token, request,
                new TypeReference<ApiResponse<Void>>() {});
    }

    public ApiResponse<List<DiningAreaDto>> listDiningAreas(String token) throws IOException, InterruptedException {
        return send("GET", "/api/v1/tables/areas", token, null, new TypeReference<>() {});
    }

    public ApiResponse<DiningAreaDto> createDiningArea(String token, SaveDiningAreaRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/tables/areas", token, request, new TypeReference<>() {});
    }

    public ApiResponse<List<DiningTableDto>> listDiningTables(String token) throws IOException, InterruptedException {
        return send("GET", "/api/v1/tables", token, null, new TypeReference<>() {});
    }

    public ApiResponse<DiningTableDto> createDiningTable(String token, SaveDiningTableRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/tables", token, request, new TypeReference<>() {});
    }

    public ApiResponse<DiningTableDto> updateTableStatus(String token, UUID tableId, UpdateTableStatusRequest request)
            throws IOException, InterruptedException {
        return send("PATCH", "/api/v1/tables/" + tableId + "/status", token, request, new TypeReference<>() {});
    }

    public ApiResponse<OrderDto> createOrder(String token, CreateOrderRequest request) throws IOException, InterruptedException {
        return send("POST", "/api/v1/orders", token, request, new TypeReference<>() {});
    }

    public ApiResponse<List<OrderSummaryDto>> searchOrders(String token, String status, Boolean held, int page, int size)
            throws IOException, InterruptedException {
        StringBuilder sb = new StringBuilder("/api/v1/orders?page=").append(page).append("&size=").append(size);
        if (status != null) {
            sb.append("&status=").append(status);
        }
        if (held != null) {
            sb.append("&held=").append(held);
        }
        return send("GET", sb.toString(), token, null, new TypeReference<>() {});
    }

    public ApiResponse<OrderDto> getOrder(String token, UUID orderId) throws IOException, InterruptedException {
        return send("GET", "/api/v1/orders/" + orderId, token, null, new TypeReference<>() {});
    }

    public ApiResponse<OrderDto> addOrderItem(String token, UUID orderId, AddOrderItemRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/orders/" + orderId + "/items", token, request, new TypeReference<>() {});
    }

    public ApiResponse<OrderDto> updateOrderItem(String token, UUID orderId, UUID itemId, UpdateOrderItemRequest request)
            throws IOException, InterruptedException {
        return send("PUT", "/api/v1/orders/" + orderId + "/items/" + itemId, token, request, new TypeReference<>() {});
    }

    public ApiResponse<OrderDto> removeOrderItem(String token, UUID orderId, UUID itemId, long expectedVersion)
            throws IOException, InterruptedException {
        return send("DELETE", "/api/v1/orders/" + orderId + "/items/" + itemId + "?expectedVersion=" + expectedVersion,
                token, null, new TypeReference<>() {});
    }

    public ApiResponse<OrderDto> applyOrderDiscount(String token, UUID orderId, ApplyDiscountRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/orders/" + orderId + "/discounts", token, request, new TypeReference<>() {});
    }

    public ApiResponse<OrderDto> holdOrder(String token, UUID orderId, long expectedVersion)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/orders/" + orderId + "/hold?expectedVersion=" + expectedVersion,
                token, null, new TypeReference<>() {});
    }

    public ApiResponse<OrderDto> retrieveHeldOrder(String token, UUID orderId, long expectedVersion)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/orders/" + orderId + "/retrieve?expectedVersion=" + expectedVersion,
                token, null, new TypeReference<>() {});
    }

    public ApiResponse<OrderDto> changeOrderType(String token, UUID orderId, ChangeOrderTypeRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/orders/" + orderId + "/order-type", token, request, new TypeReference<>() {});
    }

    public ApiResponse<OrderDto> transferOrder(String token, UUID orderId, TransferOrderRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/orders/" + orderId + "/transfer", token, request, new TypeReference<>() {});
    }

    public ApiResponse<OrderDto> sendOrderToKitchen(String token, UUID orderId, long expectedVersion)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/orders/" + orderId + "/send-to-kitchen?expectedVersion=" + expectedVersion,
                token, null, new TypeReference<>() {});
    }

    public ApiResponse<OrderDto> cancelOrder(String token, UUID orderId, CancelOrderRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/orders/" + orderId + "/cancel", token, request, new TypeReference<>() {});
    }

    public ApiResponse<List<BranchRealtimeEventDto>> fetchRecentEvents(String token, long sinceSequence)
            throws IOException, InterruptedException {
        return send("GET", "/api/v1/events/recent?sinceSequence=" + sinceSequence, token, null, new TypeReference<>() {});
    }

    public ApiResponse<List<PaymentMethodDto>> listPaymentMethods(String token) throws IOException, InterruptedException {
        return send("GET", "/api/v1/payments/methods", token, null, new TypeReference<>() {});
    }

    public ApiResponse<OrderPaymentSummaryDto> getOrderPaymentSummary(String token, UUID orderId)
            throws IOException, InterruptedException {
        return send("GET", "/api/v1/payments/orders/" + orderId, token, null, new TypeReference<>() {});
    }

    public ApiResponse<OrderPaymentSummaryDto> processPayment(String token, ProcessPaymentRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/payments/process", token, request, new TypeReference<>() {});
    }

    public ApiResponse<OrderPaymentSummaryDto> cancelPaymentAttempt(String token, UUID attemptId)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/payments/attempts/" + attemptId + "/cancel", token, null, new TypeReference<>() {});
    }

    public ApiResponse<OrderPaymentSummaryDto> issueRefund(String token, RefundRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/payments/refunds", token, request, new TypeReference<>() {});
    }

    public ApiResponse<ReconciliationReportDto> getReconciliationOverview(String token)
            throws IOException, InterruptedException {
        return send("GET", "/api/v1/payments/reconciliation", token, null, new TypeReference<>() {});
    }

    public ApiResponse<OrderPaymentSummaryDto> reconcileAttempt(String token, UUID attemptId)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/payments/attempts/" + attemptId + "/reconcile", token, null, new TypeReference<>() {});
    }

    public ApiResponse<List<CashSessionDto>> listCashSessions(String token, int page, int size)
            throws IOException, InterruptedException {
        return send("GET", "/api/v1/cash-sessions?page=" + page + "&size=" + size, token, null, new TypeReference<>() {});
    }

    public ApiResponse<CashSessionDto> getActiveCashSession(String token, UUID terminalId)
            throws IOException, InterruptedException {
        String path = terminalId != null
                ? "/api/v1/cash-sessions/active?terminalId=" + terminalId
                : "/api/v1/cash-sessions/active";
        return send("GET", path, token, null, new TypeReference<>() {});
    }

    public ApiResponse<CashSessionDto> openCashSession(String token, OpenCashSessionRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/cash-sessions/open", token, request, new TypeReference<>() {});
    }

    public ApiResponse<CashSessionDto> recordCashMovement(String token, UUID sessionId, RecordCashMovementRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/cash-sessions/" + sessionId + "/movements", token, request, new TypeReference<>() {});
    }

    public ApiResponse<CashSessionDto> closeCashSession(String token, UUID sessionId, CloseCashSessionRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/cash-sessions/" + sessionId + "/close", token, request, new TypeReference<>() {});
    }

    public ApiResponse<CashSessionDto> reconcileCashSession(String token, UUID sessionId, long expectedVersion)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/cash-sessions/" + sessionId + "/reconcile?expectedVersion=" + expectedVersion,
                token, null, new TypeReference<>() {});
    }

    public ApiResponse<CashSessionDto> reopenCashSession(String token, UUID sessionId, ReopenCashSessionRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/cash-sessions/" + sessionId + "/reopen", token, request, new TypeReference<>() {});
    }

    public ApiResponse<DrawerEventDto> openDrawerManually(String token, OpenDrawerRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/cash-sessions/drawer/open", token, request, new TypeReference<>() {});
    }

    public ApiResponse<List<ReceiptDto>> listOrderReceipts(String token, UUID orderId)
            throws IOException, InterruptedException {
        return send("GET", "/api/v1/receipts/orders/" + orderId, token, null, new TypeReference<>() {});
    }

    public ApiResponse<ReceiptDto> generateOrderReceipt(String token, UUID orderId, GenerateReceiptRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/receipts/orders/" + orderId + "/generate", token, request, new TypeReference<>() {});
    }

    public ApiResponse<ReceiptDto> reprintReceipt(String token, UUID receiptId)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/receipts/" + receiptId + "/reprint", token, null, new TypeReference<>() {});
    }

    public ApiResponse<SupervisorApprovalResponse> authorizeSupervisorAction(
            String token,
            SupervisorApprovalRequest request
    ) throws IOException, InterruptedException {
        return send("POST", "/api/v1/approvals/authorize", token, request, new TypeReference<>() {});
    }

    public ApiResponse<List<KitchenStationDto>> listKitchenStations(String token)
            throws IOException, InterruptedException {
        return send("GET", "/api/v1/kitchen/stations", token, null, new TypeReference<>() {});
    }

    public ApiResponse<KitchenStationDto> createKitchenStation(String token, SaveKitchenStationRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/kitchen/stations", token, request, new TypeReference<>() {});
    }

    public ApiResponse<List<KitchenRoutingRuleDto>> listKitchenRoutingRules(String token)
            throws IOException, InterruptedException {
        return send("GET", "/api/v1/kitchen/routing-rules", token, null, new TypeReference<>() {});
    }

    public ApiResponse<KitchenRoutingRuleDto> createKitchenRoutingRule(String token, SaveKitchenRoutingRuleRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/kitchen/routing-rules", token, request, new TypeReference<>() {});
    }

    public ApiResponse<List<KitchenTicketDto>> listStationTickets(String token, UUID stationId, String sort)
            throws IOException, InterruptedException {
        return send("GET", "/api/v1/kitchen/stations/" + stationId + "/tickets?sort=" + sort,
                token, null, new TypeReference<>() {});
    }

    public ApiResponse<KitchenTicketDto> acceptKitchenTicket(String token, UUID ticketId, KitchenTicketActionRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/kitchen/tickets/" + ticketId + "/accept", token, request, new TypeReference<>() {});
    }

    public ApiResponse<KitchenTicketDto> startKitchenTicket(String token, UUID ticketId, KitchenTicketActionRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/kitchen/tickets/" + ticketId + "/start", token, request, new TypeReference<>() {});
    }

    public ApiResponse<KitchenTicketDto> updateKitchenItemStatus(
            String token, UUID ticketId, UUID itemId, UpdateKitchenItemStatusRequest request
    ) throws IOException, InterruptedException {
        return send("POST", "/api/v1/kitchen/tickets/" + ticketId + "/items/" + itemId + "/status",
                token, request, new TypeReference<>() {});
    }

    public ApiResponse<KitchenTicketDto> markKitchenTicketReady(String token, UUID ticketId, KitchenTicketActionRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/kitchen/tickets/" + ticketId + "/ready", token, request, new TypeReference<>() {});
    }

    public ApiResponse<KitchenTicketDto> serveKitchenTicket(String token, UUID ticketId, KitchenTicketActionRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/kitchen/tickets/" + ticketId + "/serve", token, request, new TypeReference<>() {});
    }

    public ApiResponse<KitchenTicketDto> holdKitchenTicket(String token, UUID ticketId, KitchenTicketActionRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/kitchen/tickets/" + ticketId + "/hold", token, request, new TypeReference<>() {});
    }

    public ApiResponse<KitchenTicketDto> releaseKitchenTicket(String token, UUID ticketId, KitchenTicketActionRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/kitchen/tickets/" + ticketId + "/release", token, request, new TypeReference<>() {});
    }

    public ApiResponse<KitchenTicketDto> cancelKitchenTicket(String token, UUID ticketId, KitchenTicketActionRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/kitchen/tickets/" + ticketId + "/cancel", token, request, new TypeReference<>() {});
    }

    public ApiResponse<KitchenTicketDto> refireKitchenTicket(String token, UUID ticketId, KitchenTicketActionRequest request)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/kitchen/tickets/" + ticketId + "/refire", token, request, new TypeReference<>() {});
    }

    public ApiResponse<List<ExpoOrderDto>> getExpoOrders(String token) throws IOException, InterruptedException {
        return send("GET", "/api/v1/kitchen/expo", token, null, new TypeReference<>() {});
    }

    public ApiResponse<List<KitchenTicketDto>> serveExpoOrder(String token, UUID orderId)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/kitchen/expo/" + orderId + "/serve", token, null, new TypeReference<>() {});
    }

    public ApiResponse<List<KitchenUnroutedItemDto>> listOpenUnroutedItems(String token)
            throws IOException, InterruptedException {
        return send("GET", "/api/v1/kitchen/unrouted-items", token, null, new TypeReference<>() {});
    }

    public ApiResponse<KitchenTicketDto> resolveUnroutedItem(
            String token, UUID unroutedId, ResolveUnroutedItemRequest request
    ) throws IOException, InterruptedException {
        return send("POST", "/api/v1/kitchen/unrouted-items/" + unroutedId + "/resolve",
                token, request, new TypeReference<>() {});
    }

    public ApiResponse<List<KitchenPrintJobDto>> listFailedKitchenPrintJobs(String token)
            throws IOException, InterruptedException {
        return send("GET", "/api/v1/kitchen/print-jobs/failed", token, null, new TypeReference<>() {});
    }

    public ApiResponse<KitchenPrintJobDto> retryKitchenPrintJob(String token, UUID jobId)
            throws IOException, InterruptedException {
        return send("POST", "/api/v1/kitchen/print-jobs/" + jobId + "/retry", token, null, new TypeReference<>() {});
    }

    private <T> ApiResponse<T> send(
            String method,
            String path,
            String bearerToken,
            Object body,
            TypeReference<ApiResponse<T>> typeRef
    ) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(baseUri.resolve(path))
                .timeout(Duration.ofSeconds(6))
                .header("Accept", "application/json")
                .header("X-Correlation-Id", CorrelationId.generate().value());

        if (bearerToken != null && !bearerToken.isBlank()) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }

        if (body != null) {
            String json = objectMapper.writeValueAsString(body);
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json));
        } else if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.GET();
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            try {
                ApiResponse<?> errorBody = objectMapper.readValue(response.body(), new TypeReference<>() {});
                if (errorBody != null && errorBody.error() != null) {
                    throw new ApiClientException(
                            response.statusCode(),
                            errorBody.error().code(),
                            errorBody.error().message()
                    );
                }
            } catch (ApiClientException ace) {
                throw ace;
            } catch (Exception ignored) {
                // Fall through to generic HTTP error if body is non-JSON
            }
            throw new ApiClientException(response.statusCode(), "HTTP_" + response.statusCode(),
                    "Local Server returned HTTP " + response.statusCode());
        }
        return objectMapper.readValue(response.body(), typeRef);
    }
}
