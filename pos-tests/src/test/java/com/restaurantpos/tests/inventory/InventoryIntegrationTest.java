package com.restaurantpos.tests.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantpos.application.inventory.InventoryOutboxConsumerService;
import com.restaurantpos.application.kitchen.KitchenOutboxConsumerService;
import com.restaurantpos.application.security.RbacSeedService;
import com.restaurantpos.domain.kitchen.OutboxWorkItem;
import com.restaurantpos.domain.port.BranchRepositoryPort;
import com.restaurantpos.domain.port.InventoryRepositoryPort;
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
import com.restaurantpos.shared.dto.inventory.CreateStockCountRequest;
import com.restaurantpos.shared.dto.inventory.CreateStockTransferRequest;
import com.restaurantpos.shared.dto.inventory.ReceiveStockTransferRequest;
import com.restaurantpos.shared.dto.inventory.RecordStockAdjustmentRequest;
import com.restaurantpos.shared.dto.inventory.RecordWasteRequest;
import com.restaurantpos.shared.dto.inventory.SaveInventoryCategoryRequest;
import com.restaurantpos.shared.dto.inventory.SaveInventoryItemRequest;
import com.restaurantpos.shared.dto.inventory.SaveInventoryLocationRequest;
import com.restaurantpos.shared.dto.inventory.SaveRecipeRequest;
import com.restaurantpos.shared.dto.inventory.SaveUnitConversionRequest;
import com.restaurantpos.shared.dto.inventory.SaveUnitRequest;
import com.restaurantpos.shared.dto.inventory.StockCountActionRequest;
import com.restaurantpos.shared.dto.inventory.StockTransferActionRequest;
import com.restaurantpos.shared.dto.inventory.UpdateStockCountItemsRequest;
import com.restaurantpos.shared.dto.kitchen.KitchenTicketActionRequest;
import com.restaurantpos.shared.dto.kitchen.SaveKitchenRoutingRuleRequest;
import com.restaurantpos.shared.dto.kitchen.SaveKitchenStationRequest;
import com.restaurantpos.shared.dto.payment.OpenCashSessionRequest;
import com.restaurantpos.shared.dto.payment.ProcessPaymentRequest;
import com.restaurantpos.shared.dto.payment.RefundRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack PostgreSQL integration suite verifying Phase 7:
 * Flyway V013 + ddl-auto=validate, unit conversions, WAC costing, immutable movement ledger,
 * versioned recipes + sub-recipes + modifiers, reservation -> prep consumption -> supplemental/refire -> refund non-restock,
 * cancellation release, waste & alerts, physical stock counts, cross-branch transfers, RBAC & tenant isolation.
 */
@SpringBootTest(
        classes = SecurityApiTestApplication.class,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.open-in-view=false",
                "spring.flyway.enabled=true",
                "spring.flyway.locations=classpath:db/migration",
                "pos.security.max-throttle-attempts-per-key=200",
                "pos.payment.refund-approval-threshold=999.00"
        }
)
@AutoConfigureMockMvc
class InventoryIntegrationTest {

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
                    .withDatabaseName("restaurant_pos_inv_test")
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
    @Autowired private KitchenOutboxConsumerService kitchenOutboxConsumer;
    @Autowired private InventoryOutboxConsumerService inventoryOutboxConsumer;
    @Autowired private InventoryRepositoryPort inventoryRepo;

    private String tenantACode;
    private UUID tenantAId;
    private UUID branchA1Id;
    private UUID branchA2Id;
    private UUID terminalA1Id;
    private String tenantBCode;
    private UUID tenantBId;
    private UUID branchB1Id;

    @BeforeEach
    void setupTenants() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        tenantAId = UUID.randomUUID();
        tenantACode = "INVA_" + suffix;
        tenantRepo.save(new Tenant(tenantAId, tenantACode, "Prime Bistro", "Prime LLC", "USD", "ACTIVE", 0L));
        branchA1Id = UUID.randomUUID();
        branchA2Id = UUID.randomUUID();
        branchRepo.save(new Branch(branchA1Id, TenantId.of(tenantAId), "B1", "Branch 1", "UTC", "USD", true, 0L));
        branchRepo.save(new Branch(branchA2Id, TenantId.of(tenantAId), "B2", "Branch 2", "UTC", "USD", true, 0L));
        rbacSeedService.seedTenantDefaults(TenantId.of(tenantAId));
        terminalA1Id = registerTerminal(tenantAId, branchA1Id, "POS-INV-1");

        tenantBId = UUID.randomUUID();
        tenantBCode = "INVB_" + suffix;
        tenantRepo.save(new Tenant(tenantBId, tenantBCode, "Other Place", "Other LLC", "USD", "ACTIVE", 0L));
        branchB1Id = UUID.randomUUID();
        branchRepo.save(new Branch(branchB1Id, TenantId.of(tenantBId), "B1", "Other Branch", "UTC", "USD", true, 0L));
        rbacSeedService.seedTenantDefaults(TenantId.of(tenantBId));
    }

    @Test
    void fullInventoryRecipesConsumptionCountsTransfersWasteAndRbacWorkflow() throws Exception {
        createUserWithRole(tenantAId, "mgr_inv", "MgrPass!1", DefaultRoleTemplate.BRANCH_MANAGER.code(), List.of(branchA1Id, branchA2Id));
        createUserWithRole(tenantAId, "cook_inv", "CookPass!1", DefaultRoleTemplate.KITCHEN_STAFF.code(), List.of(branchA1Id));
        createUserWithRole(tenantBId, "other_mgr", "OtherPass!1", DefaultRoleTemplate.BRANCH_MANAGER.code(), List.of(branchB1Id));

        String mgrB1Token = login(tenantACode, "mgr_inv", "MgrPass!1", branchA1Id, terminalA1Id);
        String mgrB2Token = login(tenantACode, "mgr_inv", "MgrPass!1", branchA2Id, null);
        String cookToken = login(tenantACode, "cook_inv", "CookPass!1", branchA1Id, null);
        String otherToken = login(tenantBCode, "other_mgr", "OtherPass!1", branchB1Id, null);

        // 1. Units, custom item conversion (1 BOX = 24 PCS), Categories, Locations (B1 & B2)
        UUID gramId = UUID.fromString(postData(mgrB1Token, "/api/v1/inventory/units",
                new SaveUnitRequest("G", "Gram", "WEIGHT", null, BigDecimal.ONE), 201).at("/id").asText());
        UUID kgId = UUID.fromString(postData(mgrB1Token, "/api/v1/inventory/units",
                new SaveUnitRequest("KG", "Kilogram", "WEIGHT", gramId, new BigDecimal("1000")), 201).at("/id").asText());
        UUID pcsId = UUID.fromString(postData(mgrB1Token, "/api/v1/inventory/units",
                new SaveUnitRequest("PCS", "Piece", "COUNT", null, BigDecimal.ONE), 201).at("/id").asText());
        UUID boxId = UUID.fromString(postData(mgrB1Token, "/api/v1/inventory/units",
                new SaveUnitRequest("BOX", "Box", "COUNT", null, BigDecimal.ONE), 201).at("/id").asText());

        UUID catProteinsId = UUID.fromString(postData(mgrB1Token, "/api/v1/inventory/categories",
                new SaveInventoryCategoryRequest("PROT", "Proteins", "Meats & dairy", 1, true), 201).at("/id").asText());
        UUID locB1MainId = UUID.fromString(postData(mgrB1Token, "/api/v1/inventory/locations",
                new SaveInventoryLocationRequest("B1-COOL", "B1 Walk-In Cooler", "COLD_ROOM", true, true), 201).at("/id").asText());
        UUID locB2MainId = UUID.fromString(postData(mgrB2Token, "/api/v1/inventory/locations",
                new SaveInventoryLocationRequest("B2-COOL", "B2 Cooler", "COLD_ROOM", true, true), 201).at("/id").asText());

        UUID beefId = UUID.fromString(postData(mgrB1Token, "/api/v1/inventory/items",
                new SaveInventoryItemRequest(catProteinsId, locB1MainId, false, "BEEF-01", "Ground Beef",
                        "Chuck 80/20", "RAW_INGREDIENT", null, kgId, new BigDecimal("1.0"), new BigDecimal("2.0"),
                        new BigDecimal("10.00"), "STANDARD", false, false, "DENY", "WEIGHTED_AVERAGE", true),
                201).at("/id").asText());
        UUID cheeseId = UUID.fromString(postData(mgrB1Token, "/api/v1/inventory/items",
                new SaveInventoryItemRequest(catProteinsId, locB1MainId, false, "CHZ-01", "Cheddar Slice",
                        "Sliced", "RAW_INGREDIENT", null, pcsId, new BigDecimal("2.0"), new BigDecimal("5.0"),
                        new BigDecimal("0.50"), "STANDARD", false, false, "DENY", "WEIGHTED_AVERAGE", true),
                201).at("/id").asText());

        postData(mgrB1Token, "/api/v1/inventory/unit-conversions",
                new SaveUnitConversionRequest(cheeseId, boxId, pcsId, new BigDecimal("24"), true), 201);

        // Post opening balance: 5.0 kg beef @ $10 + later adjustment +5.0 kg beef @ $14 -> WAC = $12.00/kg (10.0000 kg total)
        JsonNode mv1 = postData(mgrB1Token, "/api/v1/inventory/adjustments",
                new RecordStockAdjustmentRequest(locB1MainId, beefId, kgId, new BigDecimal("5.000"),
                        "OPENING_BALANCE", new BigDecimal("10.00"), "Initial stock", null, null, "open-beef-1", null, false),
                200);
        UUID mv1Id = UUID.fromString(mv1.at("/id").asText());
        postData(mgrB1Token, "/api/v1/inventory/adjustments",
                new RecordStockAdjustmentRequest(locB1MainId, beefId, kgId, new BigDecimal("5.000"),
                        "STOCK_ADJUSTMENT_IN", new BigDecimal("14.00"), "Top-up delivery", null, null, "open-beef-2", null, false),
                200);
        // 1 BOX cheese -> 24 PCS added @ $0.50/pc
        postData(mgrB1Token, "/api/v1/inventory/adjustments",
                new RecordStockAdjustmentRequest(locB1MainId, cheeseId, boxId, BigDecimal.ONE,
                        "OPENING_BALANCE", new BigDecimal("12.00"), "1 box cheese", null, null, "open-chz-1", null, false),
                200);

        // Ledger immutability verification: DB trigger forbids UPDATE / DELETE on stock_movements
        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("UPDATE stock_movements SET quantity_delta = 999 WHERE id = ?", mv1Id));
        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("DELETE FROM stock_movements WHERE id = ?", mv1Id));

        // Check Weighted Average Cost on beef = $12.0000
        JsonNode balances1 = getData(mgrB1Token, "/api/v1/inventory/balances", 200);
        BigDecimal beefWac = findBalance(balances1, beefId).at("/averageUnitCost").decimalValue();
        assertEquals(0, new BigDecimal("12.0000").compareTo(beefWac));

        // 2. Setup Menu product, Extra Cheese modifier, Table, Kitchen Station & Recipe v1 (200g beef + 1 cheese slice; Extra Cheese = +1 slice)
        UUID taxId = UUID.fromString(postData(mgrB1Token, "/api/v1/menu/taxes",
                new CreateTaxRequest("VAT0", "No Tax", BigDecimal.ZERO, false, true), 201).at("/id").asText());
        UUID menuCatId = UUID.fromString(postData(mgrB1Token, "/api/v1/menu/categories",
                new SaveCategoryRequest(null, "BURGERS", "Burgers", 1, "#B91C1C", true), 201).at("/id").asText());
        UUID modGroupId = UUID.fromString(postData(mgrB1Token, "/api/v1/menu/modifier-groups",
                new SaveModifierGroupRequest("ADDONS", "Add-Ons", 0, 3, false, true), 201).at("/id").asText());
        UUID extraChzModId = UUID.fromString(postData(mgrB1Token, "/api/v1/menu/modifier-groups/" + modGroupId + "/modifiers",
                new SaveModifierRequest("EX_CHZ", "Extra Cheese", new BigDecimal("1.50"), 1, false, true), 201).at("/id").asText());
        UUID burgerProdId = UUID.fromString(postData(mgrB1Token, "/api/v1/menu/products",
                new SaveProductRequest(menuCatId, taxId, "BRG-CL", null, "Classic Burger", "Grilled",
                        new BigDecimal("15.00"), "FIXED", "STANDARD", null, 1, true, List.of(modGroupId)),
                201).at("/id").asText());

        UUID stationId = UUID.fromString(postData(mgrB1Token, "/api/v1/kitchen/stations",
                new SaveKitchenStationRequest("HOT", "Grill", "HOT_KITCHEN", null, 1, 10, true, 42, "EN", "UTF-8", true, true),
                201).at("/id").asText());
        postData(mgrB1Token, "/api/v1/kitchen/routing-rules",
                new SaveKitchenRoutingRuleRequest(stationId, null, menuCatId, 1, null, true), 201);

        JsonNode recipeV1 = postData(mgrB1Token, "/api/v1/inventory/recipes",
                new SaveRecipeRequest(burgerProdId, null, false, "Classic Burger Recipe", false,
                        BigDecimal.ONE, pcsId, BigDecimal.ZERO, "Grill medium", 6, true, true,
                        List.of(
                                new SaveRecipeRequest.IngredientLineInput(beefId, null, gramId, new BigDecimal("200"), BigDecimal.ZERO, false, null, null, 1),
                                new SaveRecipeRequest.IngredientLineInput(cheeseId, null, pcsId, BigDecimal.ONE, BigDecimal.ZERO, false, null, null, 2)
                        ),
                        List.of(
                                new SaveRecipeRequest.ModifierMappingInput(extraChzModId, "ADD", cheeseId, null, pcsId, BigDecimal.ONE, null, "Extra slice")
                        )), 201);
        UUID recipeId = UUID.fromString(recipeV1.at("/id").asText());

        // 3. Create Order #1 (2x Classic Burger with Extra Cheese) -> send to kitchen -> reserve 0.4000 kg beef & 4 pcs cheese
        UUID areaId = UUID.fromString(postData(mgrB1Token, "/api/v1/tables/areas",
                new SaveDiningAreaRequest("MAIN", "Main", 1, true), 201).at("/id").asText());
        JsonNode tbl = postData(mgrB1Token, "/api/v1/tables",
                new SaveDiningTableRequest(areaId, "T1", "Table 1", 4, 1, true), 201);
        JsonNode order1 = postData(mgrB1Token, "/api/v1/orders",
                new CreateOrderRequest("DINE_IN", UUID.fromString(tbl.at("/id").asText()), tbl.at("/version").asLong(), null, 2, "G1", null), 201);
        UUID order1Id = UUID.fromString(order1.at("/id").asText());
        order1 = postData(mgrB1Token, "/api/v1/orders/" + order1Id + "/items",
                new AddOrderItemRequest(burgerProdId, null, new BigDecimal("2"), null,
                        List.of(new AddOrderItemRequest.SelectedModifierInput(extraChzModId, 1)), order1.at("/version").asLong()), 200);
        order1 = postData(mgrB1Token, "/api/v1/orders/" + order1Id + "/send-to-kitchen?expectedVersion=" + order1.at("/version").asLong(), null, 200);

        kitchenOutboxConsumer.processPendingEvents(25);
        inventoryOutboxConsumer.processPendingEvents(25);

        JsonNode balAfterReserve = getData(mgrB1Token, "/api/v1/inventory/balances", 200);
        JsonNode beefRes = findBalance(balAfterReserve, beefId);
        assertEquals(0, new BigDecimal("10.0000").compareTo(beefRes.at("/onHandQuantity").decimalValue()));
        assertEquals(0, new BigDecimal("0.4000").compareTo(beefRes.at("/reservedQuantity").decimalValue()));
        assertEquals(0, new BigDecimal("9.6000").compareTo(beefRes.at("/availableQuantity").decimalValue()));
        JsonNode chzRes = findBalance(balAfterReserve, cheeseId);
        assertEquals(0, new BigDecimal("4.0000").compareTo(chzRes.at("/reservedQuantity").decimalValue()));

        // Edit recipe to v2 (500g beef!) RIGHT AFTER order1 was reserved -> starting prep MUST still consume v1 (0.4000 kg), NOT 1.000 kg!
        postData(mgrB1Token, "/api/v1/inventory/recipes/" + recipeId + "/versions",
                new SaveRecipeRequest(burgerProdId, null, false, "Classic Burger Recipe", false,
                        BigDecimal.ONE, pcsId, BigDecimal.ZERO, "Double beef v2", 8, true, true,
                        List.of(new SaveRecipeRequest.IngredientLineInput(beefId, null, gramId, new BigDecimal("500"), BigDecimal.ZERO, false, null, null, 1)),
                        List.of()), 201);

        // Start kitchen prep -> INVENTORY_CONSUME_PREP consumes 0.4000 kg beef -> onHand = 9.6000, reserved = 0, avail = 9.6000
        JsonNode kdsTickets = getData(cookToken, "/api/v1/kitchen/stations/" + stationId + "/tickets?sort=FIFO", 200);
        UUID ticketId = UUID.fromString(kdsTickets.get(0).at("/id").asText());
        long ticketVer = kdsTickets.get(0).at("/version").asLong();
        JsonNode startedTicket = postData(cookToken, "/api/v1/kitchen/tickets/" + ticketId + "/start",
                new KitchenTicketActionRequest(ticketVer, null, null), 200);
        inventoryOutboxConsumer.processPendingEvents(25);

        JsonNode balAfterPrep = getData(mgrB1Token, "/api/v1/inventory/balances", 200);
        JsonNode beefConsumed = findBalance(balAfterPrep, beefId);
        assertEquals(0, new BigDecimal("9.6000").compareTo(beefConsumed.at("/onHandQuantity").decimalValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(beefConsumed.at("/reservedQuantity").decimalValue()));

        // Replay same INV outbox event -> idempotent, balance stays 9.6000!
        UUID prepOutboxId = jdbcTemplate.queryForObject(
                "SELECT id FROM outbox_events WHERE event_type = 'INVENTORY_CONSUME_PREP' LIMIT 1", UUID.class);
        inventoryOutboxConsumer.processSingleEvent(new OutboxWorkItem(
                prepOutboxId, TenantId.of(tenantAId), BranchId.of(branchA1Id), "KitchenTicket", ticketId,
                "INVENTORY_CONSUME_PREP", "dup", "{}", "PUBLISHED", 0, Instant.now(), Instant.now()));
        assertEquals(0, new BigDecimal("9.6000").compareTo(
                findBalance(getData(mgrB1Token, "/api/v1/inventory/balances", 200), beefId).at("/onHandQuantity").decimalValue()));

        // Re-fire ticket -> consumes extra replacement batch (note ticket has burger x2, refire uses v2 0.500 * 2 = 1.0000 kg since new ticket is fired now -> 8.6000 kg)
        postData(mgrB1Token, "/api/v1/kitchen/tickets/" + ticketId + "/refire",
                new KitchenTicketActionRequest(startedTicket.at("/version").asLong(), "Dropped on pass", null), 200);
        kitchenOutboxConsumer.processPendingEvents(25);
        inventoryOutboxConsumer.processPendingEvents(25);
        assertEquals(0, new BigDecimal("8.6000").compareTo(
                findBalance(getData(mgrB1Token, "/api/v1/inventory/balances", 200), beefId).at("/onHandQuantity").decimalValue()));

        // Pay order1 and issue refund -> refund MUST NOT restock consumed ingredients (still 8.6000 kg)!
        JsonNode cashSess = postData(mgrB1Token, "/api/v1/cash-sessions/open",
                new OpenCashSessionRequest(terminalA1Id, new BigDecimal("100.00")), 201);
        UUID cashMethodId = null;
        for (JsonNode m : getData(mgrB1Token, "/api/v1/payments/methods", 200)) {
            if ("CASH".equals(m.at("/methodType").asText())) cashMethodId = UUID.fromString(m.at("/id").asText());
        }
        UUID cashSessionId = UUID.fromString(cashSess.at("/id").asText());
        JsonNode paidOrder = postData(mgrB1Token, "/api/v1/payments/process", new ProcessPaymentRequest(
                order1Id, cashMethodId, "pay-inv-1", new BigDecimal("33.00"), new BigDecimal("33.00"),
                BigDecimal.ZERO, cashSessionId, null, null), 200);
        UUID paymentId = UUID.fromString(paidOrder.at("/payments/0/id").asText());
        postData(mgrB1Token, "/api/v1/payments/refunds", new RefundRequest(
                order1Id, paymentId, "ref-inv-1", new BigDecimal("33.00"), "Customer unhappy", null, cashSessionId, null), 200);
        assertEquals(0, new BigDecimal("8.6000").compareTo(
                findBalance(getData(mgrB1Token, "/api/v1/inventory/balances", 200), beefId).at("/onHandQuantity").decimalValue()));

        // 4. Order #2: send to kitchen -> reserved 0.500 kg -> CANCEL before prep -> reservation released cleanly!
        JsonNode order2 = postData(mgrB1Token, "/api/v1/orders",
                new CreateOrderRequest("TAKEAWAY", null, null, null, 1, "Walk", null), 201);
        UUID order2Id = UUID.fromString(order2.at("/id").asText());
        order2 = postData(mgrB1Token, "/api/v1/orders/" + order2Id + "/items",
                new AddOrderItemRequest(burgerProdId, null, BigDecimal.ONE, null, List.of(), order2.at("/version").asLong()), 200);
        order2 = postData(mgrB1Token, "/api/v1/orders/" + order2Id + "/send-to-kitchen?expectedVersion=" + order2.at("/version").asLong(), null, 200);
        inventoryOutboxConsumer.processPendingEvents(25);
        assertEquals(0, new BigDecimal("0.5000").compareTo(
                findBalance(getData(mgrB1Token, "/api/v1/inventory/balances", 200), beefId).at("/reservedQuantity").decimalValue()));

        postData(mgrB1Token, "/api/v1/orders/" + order2Id + "/cancel",
                new CancelOrderRequest("Guest changed mind", null, order2.at("/version").asLong()), 200);
        inventoryOutboxConsumer.processPendingEvents(25);
        JsonNode beefAfterCancel = findBalance(getData(mgrB1Token, "/api/v1/inventory/balances", 200), beefId);
        assertEquals(0, BigDecimal.ZERO.compareTo(beefAfterCancel.at("/reservedQuantity").decimalValue()));
        assertEquals(0, new BigDecimal("8.6000").compareTo(beefAfterCancel.at("/onHandQuantity").decimalValue()));

        // 5. Waste recording (-0.6000 kg -> 8.0000 kg remaining)
        postData(mgrB1Token, "/api/v1/inventory/waste", new RecordWasteRequest(
                locB1MainId, beefId, gramId, new BigDecimal("600"), "SPOILAGE", "End of shift trim", null, "waste-1", null), 201);
        assertEquals(0, new BigDecimal("8.0000").compareTo(
                findBalance(getData(mgrB1Token, "/api/v1/inventory/balances", 200), beefId).at("/onHandQuantity").decimalValue()));

        // 6. Physical Stock Count: create -> count 7.5000 kg beef -> submit -> approve -> post -> double-post blocked
        JsonNode cnt = postData(mgrB1Token, "/api/v1/inventory/counts",
                new CreateStockCountRequest(locB1MainId, null, List.of(beefId), "Evening cycle count"), 201);
        UUID cntId = UUID.fromString(cnt.at("/id").asText());
        cnt = putData(mgrB1Token, "/api/v1/inventory/counts/" + cntId + "/items", new UpdateStockCountItemsRequest(
                cnt.at("/version").asLong(),
                List.of(new UpdateStockCountItemsRequest.CountLineInput(beefId, new BigDecimal("7.5000"), false, "Shrinkage"))), 200);
        cnt = postData(mgrB1Token, "/api/v1/inventory/counts/" + cntId + "/submit",
                new StockCountActionRequest(cnt.at("/version").asLong(), null, null), 200);
        // Unmapped variance is 0.5 / 8.0 = 6.25% > 5%, but we test mgr_inv + same creator vs count workflow: update policy or expect 403/422 without supervisor -> create independent approver!
        UUID approverId = createUserWithRole(tenantAId, "appr_inv", "ApprPass!1", DefaultRoleTemplate.ASSISTANT_MANAGER.code(), List.of(branchA1Id));
        String approverToken = login(tenantACode, "appr_inv", "ApprPass!1", branchA1Id, null);
        cnt = postData(approverToken, "/api/v1/inventory/counts/" + cntId + "/approve",
                new StockCountActionRequest(cnt.at("/version").asLong(), null, "Verified variance"), 200);
        cnt = postData(approverToken, "/api/v1/inventory/counts/" + cntId + "/post",
                new StockCountActionRequest(cnt.at("/version").asLong(), null, null), 200);
        assertEquals("POSTED", cnt.at("/status").asText());
        assertEquals(0, new BigDecimal("7.5000").compareTo(
                findBalance(getData(mgrB1Token, "/api/v1/inventory/balances", 200), beefId).at("/onHandQuantity").decimalValue()));

        // Prevent duplicate posting
        mockMvc.perform(post("/api/v1/inventory/counts/" + cntId + "/post")
                        .header("Authorization", "Bearer " + approverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StockCountActionRequest(cnt.at("/version").asLong(), null, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ALREADY_POSTED"));

        // 7. Cross-branch Stock Transfer (Branch 1 -> Branch 2): Request 3.0 kg beef -> Approve -> Dispatch -> Partial Receive (2.0 kg, then 1.0 kg)
        JsonNode trf = postData(mgrB1Token, "/api/v1/inventory/transfers", new CreateStockTransferRequest(
                locB1MainId, branchA2Id, locB2MainId, "Restock Branch 2",
                List.of(new CreateStockTransferRequest.TransferLineInput(beefId, kgId, new BigDecimal("3.0"), null))), 201);
        UUID trfId = UUID.fromString(trf.at("/id").asText());
        UUID trfItemId = UUID.fromString(trf.at("/items/0/id").asText());
        trf = postData(mgrB1Token, "/api/v1/inventory/transfers/" + trfId + "/approve",
                new StockTransferActionRequest(trf.at("/version").asLong(), "Approved"), 200);
        trf = postData(mgrB1Token, "/api/v1/inventory/transfers/" + trfId + "/dispatch",
                new StockTransferActionRequest(trf.at("/version").asLong(), "Dispatched"), 200);
        // Branch 1 onHand reduced 7.5 - 3.0 = 4.5 kg
        assertEquals(0, new BigDecimal("4.5000").compareTo(
                findBalance(getData(mgrB1Token, "/api/v1/inventory/balances", 200), beefId).at("/onHandQuantity").decimalValue()));

        // Branch 1 user CANNOT receive a transfer destined for Branch 2
        mockMvc.perform(post("/api/v1/inventory/transfers/" + trfId + "/receive")
                        .header("Authorization", "Bearer " + mgrB1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReceiveStockTransferRequest(
                                trf.at("/version").asLong(),
                                List.of(new ReceiveStockTransferRequest.ReceiveLineInput(trfItemId, new BigDecimal("2.0"), BigDecimal.ZERO, BigDecimal.ZERO, null)),
                                null))))
                .andExpect(status().isForbidden());

        // Branch 2 receives 2.0 kg partial -> PARTIALLY_RECEIVED
        trf = postData(mgrB2Token, "/api/v1/inventory/transfers/" + trfId + "/receive", new ReceiveStockTransferRequest(
                trf.at("/version").asLong(),
                List.of(new ReceiveStockTransferRequest.ReceiveLineInput(trfItemId, new BigDecimal("2.0"), BigDecimal.ZERO, BigDecimal.ZERO, "Part 1")),
                null), 200);
        assertEquals("PARTIALLY_RECEIVED", trf.at("/status").asText());
        // Branch 2 receives final 1.0 kg -> RECEIVED (3.0 kg in Branch 2 cooler)
        trf = postData(mgrB2Token, "/api/v1/inventory/transfers/" + trfId + "/receive", new ReceiveStockTransferRequest(
                trf.at("/version").asLong(),
                List.of(new ReceiveStockTransferRequest.ReceiveLineInput(trfItemId, new BigDecimal("1.0"), BigDecimal.ZERO, BigDecimal.ZERO, "Part 2")),
                null), 200);
        assertEquals("RECEIVED", trf.at("/status").asText());
        assertEquals(0, new BigDecimal("3.0000").compareTo(
                findBalance(getData(mgrB2Token, "/api/v1/inventory/balances", 200), beefId).at("/onHandQuantity").decimalValue()));

        // 8. RBAC & Cross-Tenant Isolation
        mockMvc.perform(get("/api/v1/inventory/reports/valuation").header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", "Bearer " + cookToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecordStockAdjustmentRequest(
                                locB1MainId, beefId, kgId, BigDecimal.ONE, "STOCK_ADJUSTMENT_IN",
                                null, "Unauthorized", null, null, "unauth-1", null, false))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/inventory/recipes/" + recipeId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    private static JsonNode findBalance(JsonNode list, UUID itemId) {
        for (JsonNode b : list) {
            if (itemId.toString().equals(b.at("/inventoryItemId").asText())) {
                return b;
            }
        }
        throw new AssertionError("Balance not found for item " + itemId);
    }

    private JsonNode postData(String token, String path, Object payload, int expectedStatus) throws Exception {
        var req = post(path).header("Authorization", "Bearer " + token);
        if (payload != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(payload));
        }
        MvcResult result = mockMvc.perform(req).andExpect(status().is(expectedStatus)).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data");
    }

    private JsonNode putData(String token, String path, Object payload, int expectedStatus) throws Exception {
        var req = put(path).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload));
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
