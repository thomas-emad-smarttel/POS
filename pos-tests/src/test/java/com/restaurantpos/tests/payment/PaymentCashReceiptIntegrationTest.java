package com.restaurantpos.tests.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantpos.application.security.RbacSeedService;
import com.restaurantpos.devices.CashDrawer;
import com.restaurantpos.devices.ReceiptPrinter;
import com.restaurantpos.devices.virtual.VirtualCashDrawer;
import com.restaurantpos.devices.virtual.VirtualReceiptPrinter;
import com.restaurantpos.domain.port.BranchRepositoryPort;
import com.restaurantpos.domain.port.PasswordHasherPort;
import com.restaurantpos.domain.port.RoleRepositoryPort;
import com.restaurantpos.domain.port.TenantRepositoryPort;
import com.restaurantpos.domain.port.UserRepositoryPort;
import com.restaurantpos.domain.security.DefaultRoleTemplate;
import com.restaurantpos.domain.security.RoleDefinition;
import com.restaurantpos.domain.security.UserAccount;
import com.restaurantpos.domain.security.UserRoleAssignment;
import com.restaurantpos.domain.tenancy.Branch;
import com.restaurantpos.domain.tenancy.Tenant;
import com.restaurantpos.shared.dto.payment.CloseCashSessionRequest;
import com.restaurantpos.shared.dto.payment.GenerateReceiptRequest;
import com.restaurantpos.shared.dto.payment.OpenCashSessionRequest;
import com.restaurantpos.shared.dto.payment.OpenDrawerRequest;
import com.restaurantpos.shared.dto.payment.ProcessPaymentRequest;
import com.restaurantpos.shared.dto.payment.RecordCashMovementRequest;
import com.restaurantpos.shared.dto.payment.RefundRequest;
import com.restaurantpos.shared.dto.payment.ReopenCashSessionRequest;
import com.restaurantpos.shared.dto.pos.AddOrderItemRequest;
import com.restaurantpos.shared.dto.pos.CreateOrderRequest;
import com.restaurantpos.shared.dto.pos.CreateTaxRequest;
import com.restaurantpos.shared.dto.pos.SaveCategoryRequest;
import com.restaurantpos.shared.dto.pos.SaveDiningAreaRequest;
import com.restaurantpos.shared.dto.pos.SaveDiningTableRequest;
import com.restaurantpos.shared.dto.pos.SaveProductRequest;
import com.restaurantpos.shared.dto.security.LoginRequest;
import com.restaurantpos.shared.dto.security.SupervisorApprovalRequest;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.tests.security.SecurityApiTestApplication;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack PostgreSQL integration suite verifying Phase 5 payments, split/partial tenders,
 * idempotency, card terminal timeout/reconciliation recovery, supervisor-approved refunds,
 * append-only ledger triggers, cash sessions/movements, drawer gating, and ESC/POS receipt resilience.
 */
@SpringBootTest(
        classes = SecurityApiTestApplication.class,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.open-in-view=false",
                "spring.flyway.enabled=true",
                "spring.flyway.locations=classpath:db/migration",
                "pos.security.max-throttle-attempts-per-key=200",
                "pos.payment.refund-approval-threshold=25.00"
        }
)
@AutoConfigureMockMvc
class PaymentCashReceiptIntegrationTest {

    private static PostgreSQLContainer<?> container;
    private static EmbeddedPostgres embeddedPostgres;

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) throws Exception {
        String url;
        String username;
        String password;
        boolean dockerReady = false;
        try {
            dockerReady = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            dockerReady = false;
        }
        if (dockerReady) {
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("restaurant_pos_pay_test")
                    .withUsername("pos_test")
                    .withPassword("pos_test_pw");
            container.start();
            url = container.getJdbcUrl();
            username = container.getUsername();
            password = container.getPassword();
        } else {
            embeddedPostgres = EmbeddedPostgres.builder().start();
            url = embeddedPostgres.getJdbcUrl("postgres", "postgres");
            username = "postgres";
            password = "";
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", () -> username);
        registry.add("spring.flyway.password", () -> password);
    }

    @AfterAll
    static void stopPostgres() throws Exception {
        if (container != null) container.stop();
        if (embeddedPostgres != null) embeddedPostgres.close();
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepositoryPort tenantRepo;
    @Autowired private BranchRepositoryPort branchRepo;
    @Autowired private UserRepositoryPort userRepo;
    @Autowired private RoleRepositoryPort roleRepo;
    @Autowired private PasswordHasherPort passwordHasher;
    @Autowired private RbacSeedService rbacSeedService;
    @Autowired private ReceiptPrinter receiptPrinter;
    @Autowired private CashDrawer cashDrawer;

    private String tenantACode;
    private UUID tenantAId;
    private UUID branchA1Id;
    private UUID terminalA1Id;
    private String tenantBCode;
    private UUID tenantBId;
    private UUID branchB1Id;

    @BeforeEach
    void setupTenants() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        tenantAId = UUID.randomUUID();
        tenantACode = "PAYA_" + suffix;
        tenantRepo.save(new Tenant(tenantAId, tenantACode, "Olive Kitchen", "Olive LLC", "USD", "ACTIVE", 0L));
        branchA1Id = UUID.randomUUID();
        branchRepo.save(new Branch(branchA1Id, TenantId.of(tenantAId), "B1", "Main Branch", "UTC", "USD", true, 0L));
        rbacSeedService.seedTenantDefaults(TenantId.of(tenantAId));
        terminalA1Id = registerTerminal(tenantAId, branchA1Id, "POS-PAY-1");

        tenantBId = UUID.randomUUID();
        tenantBCode = "PAYB_" + suffix;
        tenantRepo.save(new Tenant(tenantBId, tenantBCode, "Other Place", "Other LLC", "USD", "ACTIVE", 0L));
        branchB1Id = UUID.randomUUID();
        branchRepo.save(new Branch(branchB1Id, TenantId.of(tenantBId), "B1", "Branch B1", "UTC", "USD", true, 0L));
        rbacSeedService.seedTenantDefaults(TenantId.of(tenantBId));
    }

    @AfterEach
    void resetVirtualHardware() {
        if (receiptPrinter instanceof VirtualReceiptPrinter vp) {
            vp.setUnavailable(false);
            vp.setFailureMessage(null);
        }
        if (cashDrawer instanceof VirtualCashDrawer vd) {
            vd.setUnavailable(false);
            vd.setFailureMessage(null);
        }
    }

    @Test
    void fullPaymentSplitCashSessionsRefundsReconciliationDrawerAndReceiptLifecycle() throws Exception {
        createUserWithRole(tenantAId, "mgr_pay", "MgrPass!1", DefaultRoleTemplate.BRANCH_MANAGER.code(), List.of(branchA1Id));
        createUserWithRole(tenantAId, "asst_pay", "AsstPass!1", DefaultRoleTemplate.ASSISTANT_MANAGER.code(), List.of(branchA1Id));
        createUserWithRole(tenantAId, "cashier_pay", "CashPass!1", DefaultRoleTemplate.CASHIER.code(), List.of(branchA1Id));
        createUserWithRole(tenantBId, "other_mgr", "OtherPass!1", DefaultRoleTemplate.BRANCH_MANAGER.code(), List.of(branchB1Id));

        String mgrToken = login(tenantACode, "mgr_pay", "MgrPass!1", branchA1Id, terminalA1Id);
        String asstToken = login(tenantACode, "asst_pay", "AsstPass!1", branchA1Id, null);
        String cashierToken = login(tenantACode, "cashier_pay", "CashPass!1", branchA1Id, terminalA1Id);
        String otherTenantToken = login(tenantBCode, "other_mgr", "OtherPass!1", branchB1Id, null);

        // Setup menu item: $20.00 each + 10% tax = $22.00; 2 units = $44.00 total
        UUID taxId = UUID.fromString(postData(mgrToken, "/api/v1/menu/taxes",
                new CreateTaxRequest("VAT10", "VAT 10%", new BigDecimal("10.00"), false, true), 201).at("/id").asText());
        UUID catId = UUID.fromString(postData(mgrToken, "/api/v1/menu/categories",
                new SaveCategoryRequest(null, "MAINS", "Mains", 1, "#2563EB", true), 201).at("/id").asText());
        UUID productId = UUID.fromString(postData(mgrToken, "/api/v1/menu/products",
                new SaveProductRequest(catId, taxId, "STEAK-1", null, "Grilled Steak", "Ribeye",
                        new BigDecimal("20.00"), "FIXED", "STANDARD", null, 1, true, List.of()), 201)
                .at("/id").asText());
        UUID areaId = UUID.fromString(postData(mgrToken, "/api/v1/tables/areas",
                new SaveDiningAreaRequest("MAIN", "Main", 1, true), 201).at("/id").asText());
        JsonNode tableNode = postData(mgrToken, "/api/v1/tables",
                new SaveDiningTableRequest(areaId, "T1", "Table 1", 4, 1, true), 201);
        UUID tableId = UUID.fromString(tableNode.at("/id").asText());

        // Create order 1 on Table 1: $44.00 due
        JsonNode order1 = postData(cashierToken, "/api/v1/orders",
                new CreateOrderRequest("DINE_IN", tableId, tableNode.at("/version").asLong(), null, 2, "Booth 1", null), 201);
        UUID order1Id = UUID.fromString(order1.at("/id").asText());
        order1 = postData(cashierToken, "/api/v1/orders/" + order1Id + "/items",
                new AddOrderItemRequest(productId, null, new BigDecimal("2"), null, List.of(), order1.at("/version").asLong()), 200);
        assertEquals(0, new BigDecimal("44.00").compareTo(new BigDecimal(order1.at("/totalAmount").asText())));

        // Payment methods auto-seeded
        JsonNode methods = getData(cashierToken, "/api/v1/payments/methods", 200);
        UUID cashMethodId = null;
        UUID cardMethodId = null;
        for (JsonNode m : methods) {
            if ("CASH".equals(m.at("/methodType").asText())) cashMethodId = UUID.fromString(m.at("/id").asText());
            if ("CARD".equals(m.at("/methodType").asText())) cardMethodId = UUID.fromString(m.at("/id").asText());
        }

        // Cash payment without open cash session fails
        mockMvc.perform(post("/api/v1/payments/process")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProcessPaymentRequest(
                                order1Id, cashMethodId, "k-no-sess", new BigDecimal("15.00"), new BigDecimal("20.00"),
                                BigDecimal.ZERO, null, null, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CASH_SESSION_REQUIRED"));

        // 1. Open Cash Session on Terminal ($100.00 float) & reject second active session on same terminal
        JsonNode session = postData(cashierToken, "/api/v1/cash-sessions/open",
                new OpenCashSessionRequest(terminalA1Id, new BigDecimal("100.00")), 201);
        UUID sessionId = UUID.fromString(session.at("/id").asText());
        assertEquals("OPEN", session.at("/status").asText());
        assertEquals(0, new BigDecimal("100.00").compareTo(new BigDecimal(session.at("/derivedExpectedCash").asText())));

        mockMvc.perform(post("/api/v1/cash-sessions/open")
                        .header("Authorization", "Bearer " + mgrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OpenCashSessionRequest(terminalA1Id, new BigDecimal("50.00")))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ACTIVE_SESSION_EXISTS"));

        // Pay-in $20.00 & Pay-out $5.00 => derived expected cash = $115.00
        postData(cashierToken, "/api/v1/cash-sessions/" + sessionId + "/movements",
                new RecordCashMovementRequest("PAY_IN", new BigDecimal("20.00"), "Extra coins", "m-in-1"), 200);
        session = postData(cashierToken, "/api/v1/cash-sessions/" + sessionId + "/movements",
                new RecordCashMovementRequest("PAY_OUT", new BigDecimal("5.00"), "Ice bag", "m-out-1"), 200);
        assertEquals(0, new BigDecimal("115.00").compareTo(new BigDecimal(session.at("/derivedExpectedCash").asText())));

        // 2. Partial Cash Payment ($15.00 applied, $20.00 tendered -> $5.00 change, $29.00 remaining)
        int drawerOpensBefore = ((VirtualCashDrawer) cashDrawer).openCount();
        JsonNode partialPay = postData(cashierToken, "/api/v1/payments/process", new ProcessPaymentRequest(
                order1Id, cashMethodId, "idem-cash-1", new BigDecimal("15.00"), new BigDecimal("20.00"),
                BigDecimal.ZERO, sessionId, null, null), 200);
        assertEquals("OPEN", partialPay.at("/orderStatus").asText());
        assertEquals(0, new BigDecimal("15.00").compareTo(new BigDecimal(partialPay.at("/netPaidAmount").asText())));
        assertEquals(0, new BigDecimal("29.00").compareTo(new BigDecimal(partialPay.at("/remainingDue").asText())));
        assertEquals(0, new BigDecimal("5.00").compareTo(new BigDecimal(partialPay.at("/payments/0/changeAmount").asText())));
        assertEquals(drawerOpensBefore + 1, ((VirtualCashDrawer) cashDrawer).openCount());

        // Idempotent retry returns exact same state without adding a duplicate payment
        JsonNode retryCash = postData(cashierToken, "/api/v1/payments/process", new ProcessPaymentRequest(
                order1Id, cashMethodId, "idem-cash-1", new BigDecimal("15.00"), new BigDecimal("20.00"),
                BigDecimal.ZERO, sessionId, null, null), 200);
        assertEquals(1, retryCash.at("/payments").size());

        // Non-cash overpayment blocked (trying $50.00 on $29.00 remaining)
        mockMvc.perform(post("/api/v1/payments/process")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProcessPaymentRequest(
                                order1Id, cardMethodId, "idem-overpay", new BigDecimal("50.00"), new BigDecimal("50.00"),
                                BigDecimal.ZERO, null, "TERM-1", null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("OVERPAYMENT_NOT_ALLOWED"));

        // Card decline keeps order unpaid
        JsonNode declinedAttempt = postData(cashierToken, "/api/v1/payments/process", new ProcessPaymentRequest(
                order1Id, cardMethodId, "idem-card-declined", new BigDecimal("29.00"), null,
                BigDecimal.ZERO, null, "TERM-1", "DECLINED"), 200);
        assertEquals("FAILED", declinedAttempt.at("/latestAttempt/status").asText());
        assertEquals("OPEN", declinedAttempt.at("/orderStatus").asText());

        // 3. Printer unavailable during final split card payment DOES NOT roll back payment!
        ((VirtualReceiptPrinter) receiptPrinter).setUnavailable(true);
        JsonNode completedSplit = postData(cashierToken, "/api/v1/payments/process", new ProcessPaymentRequest(
                order1Id, cardMethodId, "idem-card-ok", new BigDecimal("29.00"), null,
                BigDecimal.ZERO, null, "TERM-1", "APPROVED"), 200);
        assertEquals("PAID", completedSplit.at("/orderStatus").asText());
        assertEquals(0, BigDecimal.ZERO.compareTo(new BigDecimal(completedSplit.at("/remainingDue").asText())));
        assertEquals("**** **** **** 4242", completedSplit.at("/payments/1/maskedAccount").asText());

        // Check table transitioned to CLEANING and receipt saved with PRINTER_UNAVAILABLE
        JsonNode tablesAfterPay = getData(cashierToken, "/api/v1/tables", 200);
        assertEquals("CLEANING", tablesAfterPay.get(0).at("/status").asText());
        JsonNode orderReceipts = getData(cashierToken, "/api/v1/receipts/orders/" + order1Id, 200);
        assertFalse(orderReceipts.isEmpty());
        UUID receiptId = UUID.fromString(orderReceipts.get(0).at("/id").asText());
        assertEquals("PRINTER_UNAVAILABLE", orderReceipts.get(0).at("/lastPrintStatus").asText());

        // Restore printer and reprint -> DUPLICATE printed copy + printCount incremented
        ((VirtualReceiptPrinter) receiptPrinter).setUnavailable(false);
        JsonNode reprinted = postData(cashierToken, "/api/v1/receipts/" + receiptId + "/reprint", null, 200);
        assertEquals("PRINTED", reprinted.at("/lastPrintStatus").asText());
        assertEquals(1, reprinted.at("/printCount").asInt());
        assertTrue(reprinted.at("/renderedText").asText().contains("DUPLICATE"));

        // Attempting to pay an already-PAID order fails
        mockMvc.perform(post("/api/v1/payments/process")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProcessPaymentRequest(
                                order1Id, cashMethodId, "idem-double-pay", new BigDecimal("1.00"), new BigDecimal("1.00"),
                                BigDecimal.ZERO, sessionId, null, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ORDER_NOT_PAYABLE"));

        // 4. Refunds, RBAC, supervisor non-self-approval, append-only check & failure tracking
        UUID cashPaymentId = UUID.fromString(completedSplit.at("/payments/0/id").asText());
        UUID cardPaymentId = UUID.fromString(completedSplit.at("/payments/1/id").asText());

        // Cashier lacks payments.refund -> 403
        mockMvc.perform(post("/api/v1/payments/refunds")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefundRequest(
                                order1Id, cashPaymentId, "ref-unauth", new BigDecimal("5.00"), "Test", null, sessionId, null))))
                .andExpect(status().isForbidden());

        // Assistant Manager lacks payments.reverse -> refund > threshold ($25.00) on $29.00 card payment requires supervisor approval
        mockMvc.perform(post("/api/v1/payments/refunds")
                        .header("Authorization", "Bearer " + asstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefundRequest(
                                order1Id, cardPaymentId, "ref-needs-sup", new BigDecimal("26.00"), "Big refund", null, null, null))))
                .andExpect(status().isForbidden());

        // Self-approval attempt rejected
        mockMvc.perform(post("/api/v1/approvals/authorize")
                        .header("Authorization", "Bearer " + asstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SupervisorApprovalRequest(
                                "asst_pay", "AsstPass!1", "payments.refund", "Payment", cardPaymentId.toString(), "Self", null))))
                .andExpect(status().isForbidden());

        // Branch Manager approves Assistant Manager's $26.00 card refund (but simulate REVERSE_FAIL first -> visible FAILED refund)
        UUID approvalId = UUID.fromString(postData(asstToken, "/api/v1/approvals/authorize",
                new SupervisorApprovalRequest("mgr_pay", "MgrPass!1", "payments.refund", "Payment",
                        cardPaymentId.toString(), "Customer dissatisfaction", null), 200)
                .at("/approvalId").asText());

        JsonNode failedRefund = postData(asstToken, "/api/v1/payments/refunds",
                new RefundRequest(order1Id, cardPaymentId, "ref-fail-1", new BigDecimal("26.00"),
                        "Overcooked", approvalId, null, "REVERSE_FAIL"), 200);
        assertEquals("FAILED", failedRefund.at("/refunds/0/status").asText());
        assertEquals("PAID", failedRefund.at("/orderStatus").asText());

        // Valid partial refund ($10.00 from $15.00 cash payment) -> order PARTIALLY_REFUNDED, cash movement deducts $10
        JsonNode okPartialRefund = postData(mgrToken, "/api/v1/payments/refunds",
                new RefundRequest(order1Id, cashPaymentId, "ref-ok-1", new BigDecimal("10.00"),
                        "Side item missing", null, sessionId, null), 200);
        assertEquals("PARTIALLY_REFUNDED", okPartialRefund.at("/orderStatus").asText());
        assertEquals(0, new BigDecimal("34.00").compareTo(new BigDecimal(okPartialRefund.at("/netPaidAmount").asText())));

        // Exceeding remaining $5.00 refundable amount on cashPaymentId blocked
        mockMvc.perform(post("/api/v1/payments/refunds")
                        .header("Authorization", "Bearer " + mgrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefundRequest(
                                order1Id, cashPaymentId, "ref-over", new BigDecimal("6.00"), "Too much", null, sessionId, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("REFUND_LIMIT_EXCEEDED"));

        // Verify PostgreSQL append-only trigger blocks mutating settled payment row
        assertThrows(Exception.class, () ->
                jdbcTemplate.update("UPDATE payments SET amount = 1.00 WHERE id = ?", cashPaymentId));

        // Expected cash in session: $100 + $20 - $5 + $15 (sale) - $10 (refund) = $120.00
        JsonNode latestSess = getData(cashierToken, "/api/v1/cash-sessions/" + sessionId, 200);
        assertEquals(0, new BigDecimal("120.00").compareTo(new BigDecimal(latestSess.at("/derivedExpectedCash").asText())));

        // 5. Card Terminal Timeout -> UNCERTAIN -> Query & Reconcile recovery on Order 2
        JsonNode order2 = postData(cashierToken, "/api/v1/orders",
                new CreateOrderRequest("COUNTER", null, null, null, 1, "Walk-in", null), 201);
        UUID order2Id = UUID.fromString(order2.at("/id").asText());
        order2 = postData(cashierToken, "/api/v1/orders/" + order2Id + "/items",
                new AddOrderItemRequest(productId, null, BigDecimal.ONE, null, List.of(), order2.at("/version").asLong()), 200);

        JsonNode timeoutSummary = postData(cashierToken, "/api/v1/payments/process",
                new ProcessPaymentRequest(order2Id, cardMethodId, "idem-timeout-1", new BigDecimal("22.00"), null,
                        BigDecimal.ZERO, null, "TERM-1", "TIMEOUT_THEN_APPROVED"), 200);
        assertEquals("UNCERTAIN", timeoutSummary.at("/latestAttempt/status").asText());
        assertEquals("OPEN", timeoutSummary.at("/orderStatus").asText());
        UUID uncertainAttemptId = UUID.fromString(timeoutSummary.at("/latestAttempt/id").asText());

        // Appears in reconciliation overview
        JsonNode recon = getData(mgrToken, "/api/v1/payments/reconciliation", 200);
        assertTrue(recon.at("/pendingOrUncertainAttempts").size() >= 1);
        assertTrue(recon.at("/failedRefunds").size() >= 1);

        // Reconcile queries terminal status -> settles payment -> Order 2 becomes PAID
        JsonNode reconciled = postData(mgrToken, "/api/v1/payments/attempts/" + uncertainAttemptId + "/reconcile", null, 200);
        assertEquals("PAID", reconciled.at("/orderStatus").asText());
        assertEquals("COMPLETED", reconciled.at("/latestAttempt/status").asText());

        // 6. Manual Cash Drawer (RBAC + Hardware failure handling + Audit)
        mockMvc.perform(post("/api/v1/cash-sessions/drawer/open")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OpenDrawerRequest(sessionId, terminalA1Id, "Make change", null))))
                .andExpect(status().isForbidden());

        ((VirtualCashDrawer) cashDrawer).setUnavailable(true);
        JsonNode failedDrawer = postData(mgrToken, "/api/v1/cash-sessions/drawer/open",
                new OpenDrawerRequest(sessionId, terminalA1Id, "Audit drawer check", null), 200);
        assertFalse(failedDrawer.at("/openedOk").asBoolean());

        ((VirtualCashDrawer) cashDrawer).setUnavailable(false);
        JsonNode okDrawer = postData(mgrToken, "/api/v1/cash-sessions/drawer/open",
                new OpenDrawerRequest(sessionId, terminalA1Id, "Make coins", null), 200);
        assertTrue(okDrawer.at("/openedOk").asBoolean());

        // 7. Close Cash Session ($118.50 counted vs $120.00 expected = -$1.50 variance) + Reopen + Close + Reconcile
        JsonNode closedSess = postData(cashierToken, "/api/v1/cash-sessions/" + sessionId + "/close",
                new CloseCashSessionRequest(new BigDecimal("118.50"), latestSess.at("/version").asLong()), 200);
        assertEquals("CLOSED", closedSess.at("/status").asText());
        assertEquals(0, new BigDecimal("-1.50").compareTo(new BigDecimal(closedSess.at("/varianceAmount").asText())));

        JsonNode reopenedSess = postData(mgrToken, "/api/v1/cash-sessions/" + sessionId + "/reopen",
                new ReopenCashSessionRequest("Recounting tip envelope", closedSess.at("/version").asLong()), 200);
        assertEquals("REOPENED", reopenedSess.at("/status").asText());

        JsonNode reclosedSess = postData(mgrToken, "/api/v1/cash-sessions/" + sessionId + "/close",
                new CloseCashSessionRequest(new BigDecimal("120.00"), reopenedSess.at("/version").asLong()), 200);
        assertEquals(0, BigDecimal.ZERO.compareTo(new BigDecimal(reclosedSess.at("/varianceAmount").asText())));

        JsonNode reconciledSess = postData(mgrToken, "/api/v1/cash-sessions/" + sessionId + "/reconcile?expectedVersion="
                + reclosedSess.at("/version").asLong(), null, 200);
        assertEquals("RECONCILED", reconciledSess.at("/status").asText());

        // Cross-tenant isolation check
        mockMvc.perform(get("/api/v1/payments/orders/" + order1Id).header("Authorization", "Bearer " + otherTenantToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/cash-sessions/" + sessionId).header("Authorization", "Bearer " + otherTenantToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/receipts/" + receiptId).header("Authorization", "Bearer " + otherTenantToken))
                .andExpect(status().isNotFound());

        // Verify Arabic receipt generation works
        JsonNode arReceipt = postData(mgrToken, "/api/v1/receipts/orders/" + order2Id + "/generate",
                new GenerateReceiptRequest("AR", 48, "UTF-8", true), 200);
        assertEquals("AR", arReceipt.at("/language").asText());
        assertTrue(arReceipt.at("/renderedText").asText().contains("المجموع الفرعي"));
        assertNotEquals("", arReceipt.at("/receiptNumber").asText());
    }

    private JsonNode postData(String token, String path, Object payload, int expectedStatus) throws Exception {
        var req = post(path).header("Authorization", "Bearer " + token);
        if (payload != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(payload));
        }
        MvcResult result = mockMvc.perform(req).andExpect(status().is(expectedStatus)).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data");
    }

    private JsonNode getData(String token, String path, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data");
    }

    private UUID registerTerminal(UUID tenantId, UUID branchId, String code) {
        UUID terminalId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO terminals (id, tenant_id, branch_id, terminal_code, display_name, os_platform, secret_hash, active) "
                        + "VALUES (?, ?, ?, ?, ?, 'WINDOWS', 'test-hash', TRUE)",
                terminalId, tenantId, branchId, code, "Terminal " + code
        );
        return terminalId;
    }

    private UUID createUserWithRole(UUID tenantId, String username, String rawPassword, String roleCode, List<UUID> branchIds) {
        UUID userId = UUID.randomUUID();
        TenantId tId = TenantId.of(tenantId);
        userRepo.save(new UserAccount(
                userId, tId, username, username + " Full", username + "@pos.local",
                passwordHasher.hash(rawPassword), true, 0, null, null, 0L
        ));
        userRepo.replaceBranchAccess(tId, userId, branchIds.stream().map(BranchId::of).collect(java.util.stream.Collectors.toSet()));
        RoleDefinition role = roleRepo.findByCodeAndTenant(roleCode, tId).orElseThrow();
        userRepo.replaceRoleAssignments(tId, userId, List.of(
                new UserRoleAssignment(UUID.randomUUID(), tId, userId, role.id(), role.code(), role.name(), null)
        ));
        return userId;
    }

    private String login(String tenantCode, String username, String password, UUID branchId, UUID terminalId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(tenantCode, username, password, branchId, terminalId))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/accessToken").asText();
    }
}
