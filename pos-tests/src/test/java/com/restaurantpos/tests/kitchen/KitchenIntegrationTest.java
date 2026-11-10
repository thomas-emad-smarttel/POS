package com.restaurantpos.tests.kitchen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantpos.application.kitchen.KitchenOutboxConsumerService;
import com.restaurantpos.application.security.RbacSeedService;
import com.restaurantpos.devices.ReceiptPrinter;
import com.restaurantpos.devices.virtual.VirtualReceiptPrinter;
import com.restaurantpos.domain.kitchen.KitchenTicketStatus;
import com.restaurantpos.domain.port.BranchRepositoryPort;
import com.restaurantpos.domain.port.KitchenRepositoryPort;
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
import com.restaurantpos.shared.dto.kitchen.KitchenTicketActionRequest;
import com.restaurantpos.shared.dto.kitchen.ResolveUnroutedItemRequest;
import com.restaurantpos.shared.dto.kitchen.SaveKitchenRoutingRuleRequest;
import com.restaurantpos.shared.dto.kitchen.SaveKitchenStationRequest;
import com.restaurantpos.shared.dto.kitchen.UpdateKitchenItemStatusRequest;
import com.restaurantpos.shared.dto.pos.AddOrderItemRequest;
import com.restaurantpos.shared.dto.pos.CancelOrderRequest;
import com.restaurantpos.shared.dto.pos.CreateOrderRequest;
import com.restaurantpos.shared.dto.pos.CreateTaxRequest;
import com.restaurantpos.shared.dto.pos.SaveCategoryRequest;
import com.restaurantpos.shared.dto.pos.SaveDiningAreaRequest;
import com.restaurantpos.shared.dto.pos.SaveDiningTableRequest;
import com.restaurantpos.shared.dto.pos.SaveModifierGroupRequest;
import com.restaurantpos.shared.dto.pos.SaveModifierRequest;
import com.restaurantpos.shared.dto.pos.SaveProductRequest;
import com.restaurantpos.shared.dto.security.LoginRequest;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.tests.security.SecurityApiTestApplication;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack PostgreSQL integration suite verifying Phase 6 Kitchen Display System:
 * Flyway V012 + ddl-auto=validate, station & routing rules, outbox idempotency,
 * supplemental dispatches, unrouted item resolution, item/ticket state machine & optimistic locking,
 * Expo multi-station gating, ESC/POS kitchen print failure & retry, and strict kitchen RBAC/isolation.
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
class KitchenIntegrationTest {

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
                    .withDatabaseName("restaurant_pos_kds_test")
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
    @Autowired private KitchenRepositoryPort kitchenRepo;
    @Autowired private KitchenOutboxConsumerService outboxConsumer;
    @Autowired @Qualifier("kitchenPrinter") private ReceiptPrinter kitchenPrinter;

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
        tenantACode = "KDSA_" + suffix;
        tenantRepo.save(new Tenant(tenantAId, tenantACode, "Bistro KDS", "Bistro LLC", "USD", "ACTIVE", 0L));
        branchA1Id = UUID.randomUUID();
        branchRepo.save(new Branch(branchA1Id, TenantId.of(tenantAId), "B1", "Main Kitchen", "UTC", "USD", true, 0L));
        rbacSeedService.seedTenantDefaults(TenantId.of(tenantAId));
        terminalA1Id = registerTerminal(tenantAId, branchA1Id, "POS-KDS-1");

        tenantBId = UUID.randomUUID();
        tenantBCode = "KDSB_" + suffix;
        tenantRepo.save(new Tenant(tenantBId, tenantBCode, "Other Bistro", "Other LLC", "USD", "ACTIVE", 0L));
        branchB1Id = UUID.randomUUID();
        branchRepo.save(new Branch(branchB1Id, TenantId.of(tenantBId), "B1", "Branch B1", "UTC", "USD", true, 0L));
        rbacSeedService.seedTenantDefaults(TenantId.of(tenantBId));
    }

    @AfterEach
    void resetVirtualPrinter() {
        if (kitchenPrinter instanceof VirtualReceiptPrinter vp) {
            vp.setUnavailable(false);
            vp.setFailureMessage(null);
        }
    }

    @Test
    void fullKitchenRoutingOutboxSupplementalExpoPrintingAndRbacWorkflow() throws Exception {
        createUserWithRole(tenantAId, "mgr_kds", "MgrPass!1", DefaultRoleTemplate.BRANCH_MANAGER.code(), List.of(branchA1Id));
        createUserWithRole(tenantAId, "chef_kds", "ChefPass!1", DefaultRoleTemplate.CHEF.code(), List.of(branchA1Id));
        createUserWithRole(tenantAId, "cook_kds", "CookPass!1", DefaultRoleTemplate.KITCHEN_STAFF.code(), List.of(branchA1Id));
        createUserWithRole(tenantAId, "waiter_kds", "WaitPass!1", DefaultRoleTemplate.WAITER.code(), List.of(branchA1Id));
        createUserWithRole(tenantBId, "other_mgr", "OtherPass!1", DefaultRoleTemplate.BRANCH_MANAGER.code(), List.of(branchB1Id));

        String mgrToken = login(tenantACode, "mgr_kds", "MgrPass!1", branchA1Id, terminalA1Id);
        String chefToken = login(tenantACode, "chef_kds", "ChefPass!1", branchA1Id, null);
        String cookToken = login(tenantACode, "cook_kds", "CookPass!1", branchA1Id, null);
        String waiterToken = login(tenantACode, "waiter_kds", "WaitPass!1", branchA1Id, terminalA1Id);
        String otherToken = login(tenantBCode, "other_mgr", "OtherPass!1", branchB1Id, null);

        // 1. Setup menu categories, modifiers, products, and dining table
        UUID taxId = UUID.fromString(postData(mgrToken, "/api/v1/menu/taxes",
                new CreateTaxRequest("VAT10", "VAT 10%", new BigDecimal("10.00"), false, true), 201).at("/id").asText());
        UUID grillCatId = UUID.fromString(postData(mgrToken, "/api/v1/menu/categories",
                new SaveCategoryRequest(null, "GRILL", "Grill & Mains", 1, "#DC2626", true), 201).at("/id").asText());
        UUID barCatId = UUID.fromString(postData(mgrToken, "/api/v1/menu/categories",
                new SaveCategoryRequest(null, "DRINKS", "Beverages", 2, "#0284C7", true), 201).at("/id").asText());
        UUID unmappedCatId = UUID.fromString(postData(mgrToken, "/api/v1/menu/categories",
                new SaveCategoryRequest(null, "SPECIALS", "Daily Specials", 3, "#16A34A", true), 201).at("/id").asText());

        UUID modGroupId = UUID.fromString(postData(mgrToken, "/api/v1/menu/modifier-groups",
                new SaveModifierGroupRequest("COOK_TEMP", "Cooking Temp", 0, 2, false, true), 201).at("/id").asText());
        UUID medRareModId = UUID.fromString(postData(mgrToken, "/api/v1/menu/modifier-groups/" + modGroupId + "/modifiers",
                new SaveModifierRequest("MED_RARE", "Medium Rare", BigDecimal.ZERO, 1, false, true), 201).at("/id").asText());

        UUID burgerProdId = UUID.fromString(postData(mgrToken, "/api/v1/menu/products",
                new SaveProductRequest(grillCatId, taxId, "BRG-1", null, "Wagyu Burger", "Chargrilled",
                        new BigDecimal("18.00"), "FIXED", "STANDARD", null, 1, true, List.of(modGroupId)), 201).at("/id").asText());
        UUID wingsProdId = UUID.fromString(postData(mgrToken, "/api/v1/menu/products",
                new SaveProductRequest(grillCatId, taxId, "WNG-1", null, "Spicy Wings", "Crispy",
                        new BigDecimal("12.00"), "FIXED", "STANDARD", null, 2, true, List.of()), 201).at("/id").asText());
        UUID drinkProdId = UUID.fromString(postData(mgrToken, "/api/v1/menu/products",
                new SaveProductRequest(barCatId, taxId, "DRK-1", null, "Mint Lemonade", "Fresh",
                        new BigDecimal("5.00"), "FIXED", "STANDARD", null, 3, true, List.of()), 201).at("/id").asText());
        UUID specialProdId = UUID.fromString(postData(mgrToken, "/api/v1/menu/products",
                new SaveProductRequest(unmappedCatId, taxId, "SPC-1", null, "Truffle Soup", "Seasonal",
                        new BigDecimal("14.00"), "FIXED", "STANDARD", null, 4, true, List.of()), 201).at("/id").asText());

        UUID areaId = UUID.fromString(postData(mgrToken, "/api/v1/tables/areas",
                new SaveDiningAreaRequest("MAIN", "Main Dining", 1, true), 201).at("/id").asText());
        JsonNode tableNode = postData(mgrToken, "/api/v1/tables",
                new SaveDiningTableRequest(areaId, "T7", "Table 7", 4, 1, true), 201);
        UUID tableId = UUID.fromString(tableNode.at("/id").asText());

        // 2. Configure stations (HOT & BAR, neither is branch-default so unmappedCat becomes unrouted) & routing rules
        UUID hotStationId = UUID.fromString(postData(mgrToken, "/api/v1/kitchen/stations",
                new SaveKitchenStationRequest("HOT", "Hot Kitchen", "HOT_KITCHEN", null, 1, 10, true, 42, "BILINGUAL", "UTF-8", false, true),
                201).at("/id").asText());
        UUID barStationId = UUID.fromString(postData(mgrToken, "/api/v1/kitchen/stations",
                new SaveKitchenStationRequest("BAR", "Bar", "BAR", null, 2, 4, true, 42, "EN", "UTF-8", false, true),
                201).at("/id").asText());

        postData(mgrToken, "/api/v1/kitchen/routing-rules",
                new SaveKitchenRoutingRuleRequest(hotStationId, null, grillCatId, 10, 12, true), 201);
        postData(mgrToken, "/api/v1/kitchen/routing-rules",
                new SaveKitchenRoutingRuleRequest(barStationId, null, barCatId, 10, 3, true), 201);

        // 3. Create dine-in order with Burger (with modifier), Wings (same Hot station), Lemonade (Bar station), Truffle Soup (unrouted)
        JsonNode order = postData(waiterToken, "/api/v1/orders",
                new CreateOrderRequest("DINE_IN", tableId, tableNode.at("/version").asLong(), null, 3, "VIP", null), 201);
        UUID orderId = UUID.fromString(order.at("/id").asText());

        order = postData(waiterToken, "/api/v1/orders/" + orderId + "/items",
                new AddOrderItemRequest(burgerProdId, null, BigDecimal.ONE, "Extra toasted bun",
                        List.of(new AddOrderItemRequest.SelectedModifierInput(medRareModId, 1)), order.at("/version").asLong()), 200);
        order = postData(waiterToken, "/api/v1/orders/" + orderId + "/items",
                new AddOrderItemRequest(wingsProdId, null, BigDecimal.ONE, null, List.of(), order.at("/version").asLong()), 200);
        order = postData(waiterToken, "/api/v1/orders/" + orderId + "/items",
                new AddOrderItemRequest(drinkProdId, null, new BigDecimal("2"), null, List.of(), order.at("/version").asLong()), 200);
        order = postData(waiterToken, "/api/v1/orders/" + orderId + "/items",
                new AddOrderItemRequest(specialProdId, null, BigDecimal.ONE, null, List.of(), order.at("/version").asLong()), 200);

        // Make printer unavailable during initial dispatch to prove ticket creation does NOT roll back
        ((VirtualReceiptPrinter) kitchenPrinter).setUnavailable(true);
        order = postData(waiterToken, "/api/v1/orders/" + orderId + "/send-to-kitchen?expectedVersion="
                + order.at("/version").asLong(), null, 200);
        assertEquals("IN_KITCHEN", order.at("/status").asText());

        // Rename Wagyu Burger in catalog RIGHT AFTER dispatch to prove historical ticket snapshot immutability
        mockMvc.perform(put("/api/v1/menu/products/" + burgerProdId)
                        .header("Authorization", "Bearer " + mgrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaveProductRequest(
                                grillCatId, taxId, "BRG-1", null, "Renamed Mega Burger", "Updated",
                                new BigDecimal("19.00"), "FIXED", "STANDARD", null, 1, true, List.of(modGroupId)))))
                .andExpect(status().isOk());

        // Process outbox via API and verify 1 event processed + replay idempotency
        JsonNode processedCount = postData(mgrToken, "/api/v1/kitchen/outbox/process?batchSize=20", null, 200);
        assertTrue(processedCount.asInt() >= 1);
        var outboxItems = kitchenRepo.claimPendingKitchenOutboxEvents(10, java.time.Instant.now().plusSeconds(5));
        assertTrue(outboxItems.isEmpty());

        // Check Hot Kitchen ticket: 2 items (Wagyu Burger preserving old name + Medium Rare modifier, Spicy Wings)
        JsonNode hotTickets = getData(cookToken, "/api/v1/kitchen/stations/" + hotStationId + "/tickets?sort=FIFO", 200);
        assertEquals(1, hotTickets.size());
        JsonNode hotTicket = hotTickets.get(0);
        UUID hotTicketId = UUID.fromString(hotTicket.at("/id").asText());
        assertEquals("INITIAL", hotTicket.at("/ticketKind").asText());
        assertEquals("T7", hotTicket.at("/tableLabelSnapshot").asText());
        assertEquals(2, hotTicket.at("/items").size());
        assertEquals("Wagyu Burger", hotTicket.at("/items/0/itemNameSnapshot").asText());
        assertEquals("Medium Rare", hotTicket.at("/items/0/selectedModifiers/0").asText());

        // Check Bar ticket: 1 item (Mint Lemonade)
        JsonNode barTickets = getData(cookToken, "/api/v1/kitchen/stations/" + barStationId + "/tickets?sort=FIFO", 200);
        assertEquals(1, barTickets.size());
        UUID barTicketId = UUID.fromString(barTickets.get(0).at("/id").asText());

        // Replay the same processed outbox event directly -> must remain idempotent (still 1 ticket per station!)
        var processedRowId = jdbcTemplate.queryForObject(
                "SELECT id FROM outbox_events WHERE event_type = 'ORDER_SENT_TO_KITCHEN' AND aggregate_id = ? LIMIT 1",
                UUID.class, orderId);
        outboxConsumer.processSingleEvent(new com.restaurantpos.domain.kitchen.OutboxWorkItem(
                processedRowId, TenantId.of(tenantAId), BranchId.of(branchA1Id), "ORDER", orderId,
                "ORDER_SENT_TO_KITCHEN", "dup", "{}", "PUBLISHED", 0, java.time.Instant.now(), java.time.Instant.now()));
        assertEquals(1, getData(cookToken, "/api/v1/kitchen/stations/" + hotStationId + "/tickets?sort=FIFO", 200).size());

        // 4. Verify printer failure captured cleanly and retry succeeds once printer recovers
        JsonNode failedJobs = getData(mgrToken, "/api/v1/kitchen/print-jobs/failed", 200);
        assertEquals(2, failedJobs.size());
        UUID failedHotPrintId = UUID.fromString(failedJobs.get(0).at("/id").asText());
        ((VirtualReceiptPrinter) kitchenPrinter).setUnavailable(false);
        JsonNode retriedJob = postData(mgrToken, "/api/v1/kitchen/print-jobs/" + failedHotPrintId + "/retry", null, 200);
        assertEquals("PRINTED", retriedJob.at("/status").asText());

        // 5. Verify unrouted item captured ("Truffle Soup") and manually resolved to Hot Kitchen
        JsonNode unrouted = getData(mgrToken, "/api/v1/kitchen/unrouted-items", 200);
        assertEquals(1, unrouted.size());
        UUID unroutedId = UUID.fromString(unrouted.get(0).at("/id").asText());
        assertEquals("Truffle Soup", unrouted.get(0).at("/productNameSnapshot").asText());
        JsonNode supplementalFromUnrouted = postData(mgrToken, "/api/v1/kitchen/unrouted-items/" + unroutedId + "/resolve",
                new ResolveUnroutedItemRequest(hotStationId, false), 200);
        assertEquals("SUPPLEMENTAL", supplementalFromUnrouted.at("/ticketKind").asText());
        assertTrue(getData(mgrToken, "/api/v1/kitchen/unrouted-items", 200).isEmpty());

        // 6. Add another Wings item AFTER dispatch & re-send -> creates ONLY incremental SUPPLEMENTAL ticket
        order = postData(waiterToken, "/api/v1/orders/" + orderId + "/items",
                new AddOrderItemRequest(wingsProdId, null, BigDecimal.ONE, "Extra sauce", List.of(), order.at("/version").asLong()), 200);
        order = postData(waiterToken, "/api/v1/orders/" + orderId + "/send-to-kitchen?expectedVersion="
                + order.at("/version").asLong(), null, 200);
        postData(mgrToken, "/api/v1/kitchen/outbox/process?batchSize=20", null, 200);

        JsonNode hotAfterAdd = getData(cookToken, "/api/v1/kitchen/stations/" + hotStationId + "/tickets?sort=FIFO", 200);
        assertEquals(3, hotAfterAdd.size());
        long supplementalCount = 0;
        for (JsonNode t : hotAfterAdd) {
            if ("SUPPLEMENTAL".equals(t.at("/ticketKind").asText())) supplementalCount++;
        }
        assertEquals(2, supplementalCount);

        // 7. RBAC checks on KITCHEN_STAFF (no financial endpoints, no cancel/refire)
        mockMvc.perform(get("/api/v1/payments/orders/" + orderId).header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/kitchen/tickets/" + hotTicketId + "/cancel")
                        .header("Authorization", "Bearer " + cookToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KitchenTicketActionRequest(hotTicket.at("/version").asLong(), "No", null))))
                .andExpect(status().isForbidden());

        // 8. Item-level partial readiness & optimistic locking on Hot ticket
        long vHot = hotTicket.at("/version").asLong();
        hotTicket = postData(cookToken, "/api/v1/kitchen/tickets/" + hotTicketId + "/accept",
                new KitchenTicketActionRequest(vHot, null, null), 200);
        assertEquals("ACCEPTED", hotTicket.at("/status").asText());

        // Stale version fails with 409 Conflict
        mockMvc.perform(post("/api/v1/kitchen/tickets/" + hotTicketId + "/start")
                        .header("Authorization", "Bearer " + cookToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KitchenTicketActionRequest(vHot, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONCURRENT_MODIFICATION"));

        hotTicket = postData(cookToken, "/api/v1/kitchen/tickets/" + hotTicketId + "/start",
                new KitchenTicketActionRequest(hotTicket.at("/version").asLong(), null, null), 200);
        UUID hotItem1Id = UUID.fromString(hotTicket.at("/items/0/id").asText());
        UUID hotItem2Id = UUID.fromString(hotTicket.at("/items/1/id").asText());
        long item1Ver = hotTicket.at("/items/0/version").asLong();
        long item2Ver = hotTicket.at("/items/1/version").asLong();

        // Mark item 1 READY -> ticket remains PREPARING
        hotTicket = postData(cookToken, "/api/v1/kitchen/tickets/" + hotTicketId + "/items/" + hotItem1Id + "/status",
                new UpdateKitchenItemStatusRequest("READY", item1Ver, null), 200);
        assertEquals("PREPARING", hotTicket.at("/status").asText());

        // Mark item 2 READY -> ticket auto-transitions to READY!
        hotTicket = postData(cookToken, "/api/v1/kitchen/tickets/" + hotTicketId + "/items/" + hotItem2Id + "/status",
                new UpdateKitchenItemStatusRequest("READY", item2Ver, null), 200);
        assertEquals("READY", hotTicket.at("/status").asText());

        // Expo serve MUST FAIL until Bar and supplemental Hot tickets are also READY
        mockMvc.perform(post("/api/v1/kitchen/expo/" + orderId + "/serve")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("EXPO_ORDER_NOT_READY"));

        // Chef holds & releases Bar ticket, then marks Bar & supplemental Hot tickets READY
        long barVer = barTickets.get(0).at("/version").asLong();
        JsonNode barHeld = postData(chefToken, "/api/v1/kitchen/tickets/" + barTicketId + "/hold",
                new KitchenTicketActionRequest(barVer, "Waiting for main course", null), 200);
        assertEquals("ON_HOLD", barHeld.at("/status").asText());
        JsonNode barReleased = postData(chefToken, "/api/v1/kitchen/tickets/" + barTicketId + "/release",
                new KitchenTicketActionRequest(barHeld.at("/version").asLong(), "Fire bar now", null), 200);
        postData(cookToken, "/api/v1/kitchen/tickets/" + barTicketId + "/ready",
                new KitchenTicketActionRequest(barReleased.at("/version").asLong(), null, null), 200);

        for (JsonNode t : hotAfterAdd) {
            if ("SUPPLEMENTAL".equals(t.at("/ticketKind").asText())) {
                UUID sId = UUID.fromString(t.at("/id").asText());
                JsonNode acceptedSup = postData(cookToken, "/api/v1/kitchen/tickets/" + sId + "/accept",
                        new KitchenTicketActionRequest(t.at("/version").asLong(), null, null), 200);
                postData(cookToken, "/api/v1/kitchen/tickets/" + sId + "/ready",
                        new KitchenTicketActionRequest(acceptedSup.at("/version").asLong(), null, null), 200);
            }
        }

        // Now Expo serve succeeds!
        JsonNode expoServed = postData(waiterToken, "/api/v1/kitchen/expo/" + orderId + "/serve", null, 200);
        assertFalse(expoServed.isEmpty());
        for (JsonNode st : expoServed) {
            assertEquals("SERVED", st.at("/status").asText());
        }

        // 9. Refire creates a new REFIRE ticket preserving historical log on original ticket
        JsonNode servedHot = getData(chefToken, "/api/v1/kitchen/tickets/" + hotTicketId, 200);
        JsonNode refired = postData(chefToken, "/api/v1/kitchen/tickets/" + hotTicketId + "/refire",
                new KitchenTicketActionRequest(servedHot.at("/version").asLong(), "Dropped at pass", null), 200);
        assertEquals("REFIRE", refired.at("/ticketKind").asText());
        assertEquals("HIGH", refired.at("/priorityLevel").asText());
        UUID refiredTicketId = UUID.fromString(refired.at("/id").asText());

        // Chef cancels refired ticket -> CANCELLED with audit event
        JsonNode cancelledRefire = postData(chefToken, "/api/v1/kitchen/tickets/" + refiredTicketId + "/cancel",
                new KitchenTicketActionRequest(refired.at("/version").asLong(), "Guest declined remake", null), 200);
        assertEquals("CANCELLED", cancelledRefire.at("/status").asText());

        JsonNode refireEvents = getData(mgrToken, "/api/v1/kitchen/tickets/" + refiredTicketId + "/events", 200);
        assertTrue(refireEvents.size() >= 2);

        // 10. Second order cancelled via POS -> propagates ORDER_CANCELLED_IN_KITCHEN outbox event cleanly
        JsonNode table2Node = getData(mgrToken, "/api/v1/tables", 200).get(0);
        JsonNode order2 = postData(waiterToken, "/api/v1/orders",
                new CreateOrderRequest("TAKEAWAY", null, null, null, 1, "Walk-in", null), 201);
        UUID order2Id = UUID.fromString(order2.at("/id").asText());
        order2 = postData(waiterToken, "/api/v1/orders/" + order2Id + "/items",
                new AddOrderItemRequest(drinkProdId, null, BigDecimal.ONE, null, List.of(), order2.at("/version").asLong()), 200);
        order2 = postData(waiterToken, "/api/v1/orders/" + order2Id + "/send-to-kitchen?expectedVersion="
                + order2.at("/version").asLong(), null, 200);
        postData(mgrToken, "/api/v1/kitchen/outbox/process?batchSize=20", null, 200);

        postData(mgrToken, "/api/v1/orders/" + order2Id + "/cancel",
                new CancelOrderRequest("Guest walked out", null, order2.at("/version").asLong()), 200);
        postData(mgrToken, "/api/v1/kitchen/outbox/process?batchSize=20", null, 200);

        var order2Tickets = kitchenRepo.findTicketsByOrderScoped(order2Id, TenantId.of(tenantAId), BranchId.of(branchA1Id));
        assertEquals(1, order2Tickets.size());
        assertEquals(KitchenTicketStatus.CANCELLED, order2Tickets.get(0).status());

        // Cross-tenant isolation check
        mockMvc.perform(get("/api/v1/kitchen/stations").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/v1/kitchen/tickets/" + hotTicketId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
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
