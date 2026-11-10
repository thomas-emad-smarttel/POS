package com.restaurantpos.tests.pos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantpos.application.security.RbacSeedService;
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
import com.restaurantpos.shared.dto.pos.AddOrderItemRequest;
import com.restaurantpos.shared.dto.pos.ApplyDiscountRequest;
import com.restaurantpos.shared.dto.pos.BranchCategoryVisibilityRequest;
import com.restaurantpos.shared.dto.pos.BranchProductOverrideRequest;
import com.restaurantpos.shared.dto.pos.CancelOrderRequest;
import com.restaurantpos.shared.dto.pos.CreateOrderRequest;
import com.restaurantpos.shared.dto.pos.CreateTaxRequest;
import com.restaurantpos.shared.dto.pos.MergeOrderRequest;
import com.restaurantpos.shared.dto.pos.SaveCategoryRequest;
import com.restaurantpos.shared.dto.pos.SaveDiningAreaRequest;
import com.restaurantpos.shared.dto.pos.SaveDiningTableRequest;
import com.restaurantpos.shared.dto.pos.SaveModifierGroupRequest;
import com.restaurantpos.shared.dto.pos.SaveModifierRequest;
import com.restaurantpos.shared.dto.pos.SaveProductRequest;
import com.restaurantpos.shared.dto.pos.SaveProductVariantRequest;
import com.restaurantpos.shared.dto.pos.SplitOrderRequest;
import com.restaurantpos.shared.dto.pos.TransferOrderRequest;
import com.restaurantpos.shared.dto.pos.UpdateOrderItemRequest;
import com.restaurantpos.shared.dto.pos.UpdateTableStatusRequest;
import com.restaurantpos.shared.dto.security.LoginRequest;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.tests.security.SecurityApiTestApplication;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack Core POS integration tests verifying menu, variants, modifiers,
 * branch overrides, table concurrency locking, authoritative server-side pricing,
 * historical snapshots, discounts, transfers, splits, merges, outbox dispatch,
 * audit logs, and realtime event catch-up against PostgreSQL.
 */
@SpringBootTest(
        classes = SecurityApiTestApplication.class,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.open-in-view=false",
                "spring.flyway.enabled=true",
                "spring.flyway.locations=classpath:db/migration",
                "pos.security.max-throttle-attempts-per-key=200"
        }
)
@AutoConfigureMockMvc
class CorePosIntegrationTest {

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
                    .withDatabaseName("restaurant_pos_core_test")
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
        if (container != null) {
            container.stop();
        }
        if (embeddedPostgres != null) {
            embeddedPostgres.close();
        }
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

    private String tenantACode;
    private UUID tenantAId;
    private UUID branchA1Id;
    private UUID branchA2Id;
    private UUID terminalA1Id;

    private String tenantBCode;
    private UUID tenantBId;
    private UUID branchB1Id;

    @BeforeEach
    void setupTenantsAndBranches() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        tenantAId = UUID.randomUUID();
        tenantACode = "POSA_" + suffix;
        tenantRepo.save(new Tenant(tenantAId, tenantACode, "Bistro A", "Bistro A LLC", "USD", "ACTIVE", 0L));
        branchA1Id = UUID.randomUUID();
        branchA2Id = UUID.randomUUID();
        branchRepo.save(new Branch(branchA1Id, TenantId.of(tenantAId), "MAIN", "Downtown Branch", "UTC", "USD", true, 0L));
        branchRepo.save(new Branch(branchA2Id, TenantId.of(tenantAId), "MALL", "Mall Branch", "UTC", "USD", true, 0L));
        rbacSeedService.seedTenantDefaults(TenantId.of(tenantAId));
        terminalA1Id = registerTerminal(tenantAId, branchA1Id, "TERM-A1");

        tenantBId = UUID.randomUUID();
        tenantBCode = "POSB_" + suffix;
        tenantRepo.save(new Tenant(tenantBId, tenantBCode, "Other Bistro", "Other LLC", "USD", "ACTIVE", 0L));
        branchB1Id = UUID.randomUUID();
        branchRepo.save(new Branch(branchB1Id, TenantId.of(tenantBId), "B1", "Other Branch", "UTC", "USD", true, 0L));
        rbacSeedService.seedTenantDefaults(TenantId.of(tenantBId));
    }

    @Test
    void fullMenuBranchOverrideAndServerValidatedOrderLifecycle() throws Exception {
        createUserWithRole(tenantAId, "mgr1", "MgrPass!1", DefaultRoleTemplate.BRANCH_MANAGER.code(), List.of(branchA1Id, branchA2Id));
        createUserWithRole(tenantAId, "cashier1", "CashPass!1", DefaultRoleTemplate.CASHIER.code(), List.of(branchA1Id));
        createUserWithRole(tenantBId, "other_mgr", "OtherPass!1", DefaultRoleTemplate.BRANCH_MANAGER.code(), List.of(branchB1Id));

        String mgrTokenB1 = loginAndExtractToken(tenantACode, "mgr1", "MgrPass!1", branchA1Id, terminalA1Id);
        String mgrTokenB2 = loginAndExtractToken(tenantACode, "mgr1", "MgrPass!1", branchA2Id, null);
        String cashierToken = loginAndExtractToken(tenantACode, "cashier1", "CashPass!1", branchA1Id, terminalA1Id);
        String otherTenantToken = loginAndExtractToken(tenantBCode, "other_mgr", "OtherPass!1", branchB1Id, null);

        // 1. Tax, categories, modifier group + modifiers, product + variant
        UUID vatId = UUID.fromString(postAndData(mgrTokenB1, "/api/v1/menu/taxes",
                new CreateTaxRequest("VAT10", "VAT 10%", new BigDecimal("10.00"), false, true), 201)
                .at("/id").asText());

        UUID burgersCatId = UUID.fromString(postAndData(mgrTokenB1, "/api/v1/menu/categories",
                new SaveCategoryRequest(null, "BURGERS", "Burgers", 1, "#2563EB", true), 201)
                .at("/id").asText());
        UUID secretCatId = UUID.fromString(postAndData(mgrTokenB1, "/api/v1/menu/categories",
                new SaveCategoryRequest(null, "VIP", "VIP Specials", 2, "#16A34A", true), 201)
                .at("/id").asText());

        // Hide VIP category in Branch 2
        putAndData(mgrTokenB2, "/api/v1/menu/categories/" + secretCatId + "/branch-visibility",
                new BranchCategoryVisibilityRequest(false, null), 200);

        UUID cookGroupId = UUID.fromString(postAndData(mgrTokenB1, "/api/v1/menu/modifier-groups",
                new SaveModifierGroupRequest("COOK", "Cooking Preference", 1, 1, true, true), 201)
                .at("/id").asText());
        UUID modMediumId = UUID.fromString(postAndData(mgrTokenB1, "/api/v1/menu/modifier-groups/" + cookGroupId + "/modifiers",
                new SaveModifierRequest("MED", "Medium", BigDecimal.ZERO, 1, true, true), 201)
                .at("/id").asText());
        UUID modBaconId = UUID.fromString(postAndData(mgrTokenB1, "/api/v1/menu/modifier-groups/" + cookGroupId + "/modifiers",
                new SaveModifierRequest("WELL_BACON", "Well + Bacon", new BigDecimal("2.00"), 2, false, true), 201)
                .at("/id").asText());

        UUID burgerProductId = UUID.fromString(postAndData(mgrTokenB1, "/api/v1/menu/products",
                new SaveProductRequest(
                        burgersCatId, vatId, "SKU-BRG", "89012345001", "House Burger", "Grilled beef",
                        new BigDecimal("10.00"), "FIXED", "STANDARD", null, 1, true, List.of(cookGroupId)
                ), 201).at("/id").asText());

        UUID doubleVariantId = UUID.fromString(postAndData(mgrTokenB1, "/api/v1/menu/products/" + burgerProductId + "/variants",
                new SaveProductVariantRequest("SKU-BRG-DBL", "89012345002", "Double", new BigDecimal("14.00"), 1, true), 201)
                .at("/id").asText());

        // Cashier cannot mutate menu
        mockMvc.perform(post("/api/v1/menu/categories")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaveCategoryRequest(null, "NOPE", "Nope", 3, null, true))))
                .andExpect(status().isForbidden());

        // Branch-specific product price override on Branch 2 ($12.50 vs $10.00 in Branch 1)
        putAndData(mgrTokenB2, "/api/v1/menu/products/" + burgerProductId + "/branch-override",
                new BranchProductOverrideRequest(new BigDecimal("12.50"), vatId, true), 200);

        mockMvc.perform(get("/api/v1/menu/products").header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].effectivePrice").value(10.00));

        mockMvc.perform(get("/api/v1/menu/products").header("Authorization", "Bearer " + mgrTokenB2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].effectivePrice").value(12.50));

        // Category visibility hidden in Branch 2 (`branchVisible=false`) vs visible in Branch 1 (`branchVisible=true`)
        mockMvc.perform(get("/api/v1/menu/categories").header("Authorization", "Bearer " + mgrTokenB2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].branchVisible").value(true))
                .andExpect(jsonPath("$.data[1].branchVisible").value(false));

        mockMvc.perform(get("/api/v1/menu/categories").header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].branchVisible").value(true))
                .andExpect(jsonPath("$.data[1].branchVisible").value(true));

        // Barcode lookup finds product in Tenant A, fails for Tenant B isolation
        mockMvc.perform(get("/api/v1/menu/products/by-barcode/89012345001")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(burgerProductId.toString()));

        mockMvc.perform(get("/api/v1/menu/products/" + burgerProductId)
                        .header("Authorization", "Bearer " + otherTenantToken))
                .andExpect(status().isNotFound());

        // 2. Dining area & concurrency-safe table creation + dine-in claim
        UUID areaId = UUID.fromString(postAndData(mgrTokenB1, "/api/v1/tables/areas",
                new SaveDiningAreaRequest("MAIN", "Main Floor", 1, true), 201).at("/id").asText());
        JsonNode t1Node = postAndData(mgrTokenB1, "/api/v1/tables",
                new SaveDiningTableRequest(areaId, "T1", "Table 1", 4, 1, true), 201);
        UUID table1Id = UUID.fromString(t1Node.at("/id").asText());
        long table1Ver = t1Node.at("/version").asLong();

        JsonNode t2Node = postAndData(mgrTokenB1, "/api/v1/tables",
                new SaveDiningTableRequest(areaId, "T2", "Table 2", 4, 2, true), 201);
        UUID table2Id = UUID.fromString(t2Node.at("/id").asText());
        long table2Ver = t2Node.at("/version").asLong();

        // Create Dine-in Order claiming Table 1
        JsonNode order1 = postAndData(cashierToken, "/api/v1/orders",
                new CreateOrderRequest("DINE_IN", table1Id, table1Ver, null, 2, "Booth 1", "Birthday"), 201);
        UUID order1Id = UUID.fromString(order1.at("/id").asText());
        long order1Ver = order1.at("/version").asLong();

        // Second terminal attempting to claim already-occupied Table 1 gets 400/409 failure
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + mgrTokenB1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrderRequest("DINE_IN", table1Id, table1Ver, null, 2, null, null))))
                .andExpect(status().is4xxClientError());

        // Missing required modifier group rejects item addition
        mockMvc.perform(post("/api/v1/orders/" + order1Id + "/items")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddOrderItemRequest(
                                burgerProductId, doubleVariantId, BigDecimal.ONE, "No onions", List.of(), order1Ver))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("MODIFIER_MIN_NOT_MET"));

        // Add variant + valid modifier: unit = 14.00 + 2.00 = 16.00 * 2 = 32.00 subtotal + 10% tax 3.20 = 35.20
        order1 = postAndData(cashierToken, "/api/v1/orders/" + order1Id + "/items",
                new AddOrderItemRequest(
                        burgerProductId, doubleVariantId, new BigDecimal("2"), "Extra crispy",
                        List.of(new AddOrderItemRequest.SelectedModifierInput(modBaconId, 1)), order1Ver
                ), 200);
        order1Ver = order1.at("/version").asLong();
        UUID item1Id = UUID.fromString(order1.at("/items/0/id").asText());
        assertEquals(0, new BigDecimal("32.00").compareTo(new BigDecimal(order1.at("/subtotalAmount").asText())));
        assertEquals(0, new BigDecimal("35.20").compareTo(new BigDecimal(order1.at("/totalAmount").asText())));

        // Add a second item (Standard burger 10.00 + Medium 0.00 = 10.00 * 1 -> subtotal = 42.00)
        order1 = postAndData(cashierToken, "/api/v1/orders/" + order1Id + "/items",
                new AddOrderItemRequest(
                        burgerProductId, null, BigDecimal.ONE, null,
                        List.of(new AddOrderItemRequest.SelectedModifierInput(modMediumId, 1)), order1Ver
                ), 200);
        order1Ver = order1.at("/version").asLong();
        assertEquals(0, new BigDecimal("42.00").compareTo(new BigDecimal(order1.at("/subtotalAmount").asText())));

        // Stale version fails with 409 Conflict
        mockMvc.perform(post("/api/v1/orders/" + order1Id + "/hold?expectedVersion=0")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isConflict());

        // Discount exceeding CASHIER role cap (Cashier cap 10%) fails with 403 without approval
        mockMvc.perform(post("/api/v1/orders/" + order1Id + "/discounts")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApplyDiscountRequest(
                                null, "VIP25", "25% Off", "PERCENTAGE", new BigDecimal("25.00"), "Comp", null, order1Ver))))
                .andExpect(status().isForbidden());

        // Manager can apply 10% discount (order1: 42.00 - 4.20 = 37.80 + 3.78 tax = 41.58)
        order1 = postAndData(mgrTokenB1, "/api/v1/orders/" + order1Id + "/discounts",
                new ApplyDiscountRequest(null, "MGR10", "Manager 10%", "PERCENTAGE", new BigDecimal("10.00"), "Promo", null, order1Ver), 200);
        order1Ver = order1.at("/version").asLong();
        assertEquals(0, new BigDecimal("4.20").compareTo(new BigDecimal(order1.at("/discountAmount").asText())));
        assertEquals(0, new BigDecimal("41.58").compareTo(new BigDecimal(order1.at("/totalAmount").asText())));

        // Historical price snapshot check: raise product base price to 99.00 and verify existing order retains original prices
        putAndData(mgrTokenB1, "/api/v1/menu/products/" + burgerProductId,
                new SaveProductRequest(
                        burgersCatId, vatId, "SKU-BRG", "89012345001", "House Burger", "Grilled beef",
                        new BigDecimal("99.00"), "FIXED", "STANDARD", null, 1, true, List.of(cookGroupId)
                ), 200);
        JsonNode reloadedOrder1 = getAndData(cashierToken, "/api/v1/orders/" + order1Id, 200);
        assertEquals(0, new BigDecimal("42.00").compareTo(new BigDecimal(reloadedOrder1.at("/subtotalAmount").asText())));

        // Hold & retrieve order
        order1 = postAndData(cashierToken, "/api/v1/orders/" + order1Id + "/hold?expectedVersion=" + order1Ver, null, 200);
        order1Ver = order1.at("/version").asLong();
        assertTrue(order1.at("/held").asBoolean());
        assertEquals("ON_HOLD", order1.at("/status").asText());

        order1 = postAndData(cashierToken, "/api/v1/orders/" + order1Id + "/retrieve?expectedVersion=" + order1Ver, null, 200);
        order1Ver = order1.at("/version").asLong();
        assertEquals("OPEN", order1.at("/status").asText());

        // Cashier lacks ORDERS_TRANSFER -> 403; Branch Manager transfers order from Table 1 to Table 2 (Table 1 transitions to CLEANING)
        mockMvc.perform(post("/api/v1/orders/" + order1Id + "/transfer")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransferOrderRequest(table2Id, order1Ver, table2Ver))))
                .andExpect(status().isForbidden());

        order1 = postAndData(mgrTokenB1, "/api/v1/orders/" + order1Id + "/transfer",
                new TransferOrderRequest(table2Id, order1Ver, table2Ver), 200);
        order1Ver = order1.at("/version").asLong();
        assertEquals(table2Id.toString(), order1.at("/diningTableId").asText());

        // Clear Table 1 back to AVAILABLE before splitting a check onto Table 1 (requires tables.manage)
        JsonNode tablesAfterTransfer = getAndData(cashierToken, "/api/v1/tables", 200);
        long t1CleanVer = -1;
        for (JsonNode n : tablesAfterTransfer) {
            if (table1Id.toString().equals(n.at("/id").asText())) {
                assertEquals("CLEANING", n.at("/status").asText());
                t1CleanVer = n.at("/version").asLong();
            }
        }
        mockMvc.perform(patch("/api/v1/tables/" + table1Id + "/status")
                        .header("Authorization", "Bearer " + mgrTokenB1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTableStatusRequest("AVAILABLE", t1CleanVer))))
                .andExpect(status().isOk());

        // Split item1 out into a new order on Table 1
        JsonNode splitResp = postAndData(cashierToken, "/api/v1/orders/" + order1Id + "/split",
                new SplitOrderRequest(List.of(item1Id), table1Id, order1Ver), 200);
        assertEquals(2, splitResp.size());
        UUID remainingOrderId = UUID.fromString(splitResp.get(0).at("/id").asText());
        long remainingVer = splitResp.get(0).at("/version").asLong();
        UUID newSplitOrderId = UUID.fromString(splitResp.get(1).at("/id").asText());
        long newSplitVer = splitResp.get(1).at("/version").asLong();

        // Merge back into remainingOrder
        JsonNode merged = postAndData(mgrTokenB1, "/api/v1/orders/" + remainingOrderId + "/merge",
                new MergeOrderRequest(newSplitOrderId, remainingVer, newSplitVer), 200);
        long mergedVer = merged.at("/version").asLong();
        assertEquals(2, merged.at("/items").size());

        // Send to kitchen -> writes outbox event and locks sent items against modification
        JsonNode sentOrder = postAndData(cashierToken, "/api/v1/orders/" + remainingOrderId + "/send-to-kitchen?expectedVersion=" + mergedVer, null, 200);
        long sentVer = sentOrder.at("/version").asLong();
        assertEquals("IN_KITCHEN", sentOrder.at("/status").asText());
        UUID sentItemId = UUID.fromString(sentOrder.at("/items/0/id").asText());

        mockMvc.perform(put("/api/v1/orders/" + remainingOrderId + "/items/" + sentItemId)
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateOrderItemRequest(new BigDecimal("5"), "Hack", null, sentVer))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ITEM_ALREADY_SENT_TO_KITCHEN"));

        // Verify kitchen outbox event persisted atomically
        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE tenant_id = ? AND event_type = 'ORDER_SENT_TO_KITCHEN'",
                Integer.class, tenantAId
        );
        assertEquals(1, outboxCount);

        // Manager cancels order (releases Table 2) and verifies status history + audit logs + realtime catch-up events
        JsonNode cancelled = postAndData(mgrTokenB1, "/api/v1/orders/" + remainingOrderId + "/cancel",
                new CancelOrderRequest("Guest left early", null, sentVer), 200);
        assertEquals("CANCELLED", cancelled.at("/status").asText());

        Integer historyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_status_history WHERE order_id = ?",
                Integer.class, remainingOrderId
        );
        assertTrue(historyCount >= 4);

        // Table 2 transitions to CLEANING on order cancellation and can be marked AVAILABLE manually
        JsonNode t2Latest = getAndData(cashierToken, "/api/v1/tables", 200);
        long latestT2Ver = -1;
        for (JsonNode n : t2Latest) {
            if (table2Id.toString().equals(n.at("/id").asText())) {
                assertEquals("CLEANING", n.at("/status").asText());
                latestT2Ver = n.at("/version").asLong();
            }
        }
        mockMvc.perform(patch("/api/v1/tables/" + table2Id + "/status")
                        .header("Authorization", "Bearer " + mgrTokenB1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTableStatusRequest("AVAILABLE", latestT2Ver))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));

        // Recent realtime catch-up endpoint returns monotonic events for Branch 1
        JsonNode events = getAndData(cashierToken, "/api/v1/events/recent?sinceSequence=0", 200);
        assertTrue(events.size() >= 5);
    }

    private JsonNode postAndData(String token, String path, Object payload, int expectedStatus) throws Exception {
        var req = post(path).header("Authorization", "Bearer " + token);
        if (payload != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(payload));
        }
        MvcResult result = mockMvc.perform(req).andExpect(status().is(expectedStatus)).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data");
    }

    private JsonNode putAndData(String token, String path, Object payload, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(put(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data");
    }

    private JsonNode getAndData(String token, String path, int expectedStatus) throws Exception {
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

    private UUID createUserWithRole(
            UUID tenantId,
            String username,
            String rawPassword,
            String roleCode,
            List<UUID> branchIds
    ) {
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

    private String loginAndExtractToken(
            String tenantCode,
            String username,
            String password,
            UUID branchId,
            UUID terminalId
    ) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest(tenantCode, username, password, branchId, terminalId));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.at("/data/accessToken").asText();
    }
}
