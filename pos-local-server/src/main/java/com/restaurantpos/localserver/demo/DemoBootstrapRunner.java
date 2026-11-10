package com.restaurantpos.localserver.demo;

import com.restaurantpos.application.inventory.InventoryCatalogService;
import com.restaurantpos.application.inventory.RecipeManagementService;
import com.restaurantpos.application.inventory.StockLedgerService;
import com.restaurantpos.application.kitchen.KitchenConfigService;
import com.restaurantpos.application.payment.CashSessionService;
import com.restaurantpos.application.payment.PaymentService;
import com.restaurantpos.application.pos.MenuManagementService;
import com.restaurantpos.application.pos.TableManagementService;
import com.restaurantpos.application.security.EffectivePermissionResolver;
import com.restaurantpos.application.security.RbacSeedService;
import com.restaurantpos.domain.port.BranchRepositoryPort;
import com.restaurantpos.domain.port.PasswordHasherPort;
import com.restaurantpos.domain.port.PermissionCatalogPort;
import com.restaurantpos.domain.port.RoleRepositoryPort;
import com.restaurantpos.domain.port.TenantQueryPort;
import com.restaurantpos.domain.port.TenantRepositoryPort;
import com.restaurantpos.domain.port.UserRepositoryPort;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.domain.security.DefaultRoleTemplate;
import com.restaurantpos.domain.security.RoleDefinition;
import com.restaurantpos.domain.security.UserAccount;
import com.restaurantpos.domain.security.UserRoleAssignment;
import com.restaurantpos.domain.tenancy.Branch;
import com.restaurantpos.domain.tenancy.Tenant;
import com.restaurantpos.shared.dto.inventory.RecordStockAdjustmentRequest;
import com.restaurantpos.shared.dto.inventory.SaveInventoryCategoryRequest;
import com.restaurantpos.shared.dto.inventory.SaveInventoryItemRequest;
import com.restaurantpos.shared.dto.inventory.SaveInventoryLocationRequest;
import com.restaurantpos.shared.dto.inventory.SaveRecipeRequest;
import com.restaurantpos.shared.dto.inventory.SaveUnitRequest;
import com.restaurantpos.shared.dto.kitchen.SaveKitchenRoutingRuleRequest;
import com.restaurantpos.shared.dto.kitchen.SaveKitchenStationRequest;
import com.restaurantpos.shared.dto.payment.OpenCashSessionRequest;
import com.restaurantpos.shared.dto.pos.CreateTaxRequest;
import com.restaurantpos.shared.dto.pos.SaveCategoryRequest;
import com.restaurantpos.shared.dto.pos.SaveDiningAreaRequest;
import com.restaurantpos.shared.dto.pos.SaveDiningTableRequest;
import com.restaurantpos.shared.dto.pos.SaveModifierGroupRequest;
import com.restaurantpos.shared.dto.pos.SaveModifierRequest;
import com.restaurantpos.shared.dto.pos.SaveProductRequest;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.model.TenantId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Idempotently seeds a realistic demo restaurant tenant ({@code DEMO_GROUP}), users, branches,
 * terminal, menu, dining tables, cash session, kitchen stations/routing, and inventory/recipes
 * when running with the {@code demo} Spring profile.
 */
@Component
@Profile("demo")
@Order(100)
public class DemoBootstrapRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DemoBootstrapRunner.class);

    public static final String DEMO_TENANT_CODE = "DEMO_GROUP";
    public static final UUID DEMO_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID MAIN_BRANCH_ID = UUID.fromString("22222222-2222-2222-2222-222222222201");
    public static final UUID WEST_BRANCH_ID = UUID.fromString("22222222-2222-2222-2222-222222222202");
    public static final UUID DEMO_TERMINAL_ID = UUID.fromString("33333333-3333-3333-3333-333333333301");

    private final TenantQueryPort tenantQuery;
    private final TenantRepositoryPort tenantRepo;
    private final BranchRepositoryPort branchRepo;
    private final UserRepositoryPort userRepo;
    private final RoleRepositoryPort roleRepo;
    private final PasswordHasherPort passwordHasher;
    private final PermissionCatalogPort permissionCatalogPort;
    private final RbacSeedService rbacSeedService;
    private final EffectivePermissionResolver permissionResolver;
    private final MenuManagementService menuService;
    private final TableManagementService tableService;
    private final PaymentService paymentService;
    private final CashSessionService cashSessionService;
    private final KitchenConfigService kitchenConfigService;
    private final InventoryCatalogService inventoryCatalogService;
    private final StockLedgerService stockLedgerService;
    private final RecipeManagementService recipeService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate txTemplate;

    public DemoBootstrapRunner(
            TenantQueryPort tenantQuery,
            TenantRepositoryPort tenantRepo,
            BranchRepositoryPort branchRepo,
            UserRepositoryPort userRepo,
            RoleRepositoryPort roleRepo,
            PasswordHasherPort passwordHasher,
            PermissionCatalogPort permissionCatalogPort,
            RbacSeedService rbacSeedService,
            EffectivePermissionResolver permissionResolver,
            MenuManagementService menuService,
            TableManagementService tableService,
            PaymentService paymentService,
            CashSessionService cashSessionService,
            KitchenConfigService kitchenConfigService,
            InventoryCatalogService inventoryCatalogService,
            StockLedgerService stockLedgerService,
            RecipeManagementService recipeService,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager txManager
    ) {
        this.tenantQuery = tenantQuery;
        this.tenantRepo = tenantRepo;
        this.branchRepo = branchRepo;
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.passwordHasher = passwordHasher;
        this.permissionCatalogPort = permissionCatalogPort;
        this.rbacSeedService = rbacSeedService;
        this.permissionResolver = permissionResolver;
        this.menuService = menuService;
        this.tableService = tableService;
        this.paymentService = paymentService;
        this.cashSessionService = cashSessionService;
        this.kitchenConfigService = kitchenConfigService;
        this.inventoryCatalogService = inventoryCatalogService;
        this.stockLedgerService = stockLedgerService;
        this.recipeService = recipeService;
        this.jdbcTemplate = jdbcTemplate;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        txTemplate.executeWithoutResult(status -> seedDemoData());
    }

    private void seedDemoData() {
        if (tenantQuery.findByCode(DEMO_TENANT_CODE).isPresent()) {
            LOG.info("Demo tenant '{}' already exists; skipping seed.", DEMO_TENANT_CODE);
            return;
        }

        permissionCatalogPort.ensureCatalogSynced();
        CorrelationId corr = CorrelationId.of("demo-seed");

        tenantRepo.save(new Tenant(
                DEMO_TENANT_ID, DEMO_TENANT_CODE, "Bistro Demo Group",
                "Bistro Demo Hospitality LLC", "USD", "ACTIVE", 0L
        ));
        TenantId tenantId = TenantId.of(DEMO_TENANT_ID);

        branchRepo.save(new Branch(MAIN_BRANCH_ID, tenantId, "MAIN", "Downtown Flagship", "UTC", "USD", true, 0L));
        branchRepo.save(new Branch(WEST_BRANCH_ID, tenantId, "WEST", "Westside Kitchen", "UTC", "USD", true, 0L));

        rbacSeedService.seedTenantDefaults(tenantId);

        jdbcTemplate.update(
                "INSERT INTO terminals (id, tenant_id, branch_id, terminal_code, display_name, os_platform, secret_hash, active) "
                        + "VALUES (?, ?, ?, ?, ?, 'WINDOWS', 'demo-terminal-hash', TRUE)",
                DEMO_TERMINAL_ID, DEMO_TENANT_ID, MAIN_BRANCH_ID, "TERM-01", "Front Counter POS #1"
        );

        UserAccount adminUser = createUser(tenantId, "admin", "Demo Super Admin", "admin@demo.pos",
                "Admin123!", DefaultRoleTemplate.SUPER_ADMIN.code(), Set.of(MAIN_BRANCH_ID, WEST_BRANCH_ID));
        createUser(tenantId, "manager", "Branch Manager", "manager@demo.pos",
                "Manager123!", DefaultRoleTemplate.BRANCH_MANAGER.code(), Set.of(MAIN_BRANCH_ID, WEST_BRANCH_ID));
        createUser(tenantId, "cashier", "Front Cashier", "cashier@demo.pos",
                "Cashier123!", DefaultRoleTemplate.CASHIER.code(), Set.of(MAIN_BRANCH_ID));
        createUser(tenantId, "chef", "Line Chef", "chef@demo.pos",
                "Chef123!", DefaultRoleTemplate.KITCHEN_STAFF.code(), Set.of(MAIN_BRANCH_ID));

        AccessPrincipal mainAdmin = permissionResolver.resolve(
                adminUser, BranchId.of(MAIN_BRANCH_ID), DEMO_TERMINAL_ID, UUID.randomUUID());
        AccessPrincipal westAdmin = permissionResolver.resolve(
                adminUser, BranchId.of(WEST_BRANCH_ID), null, UUID.randomUUID());

        seedMenu(mainAdmin, corr);
        seedTables(mainAdmin, corr);
        paymentService.listPaymentMethods(mainAdmin);
        cashSessionService.openSession(mainAdmin, new OpenCashSessionRequest(DEMO_TERMINAL_ID, new BigDecimal("200.00")), corr);

        LOG.info("=================================================================");
        LOG.info(" POS DEMO READY — Tenant: {} | User: admin / Admin123!", DEMO_TENANT_CODE);
        LOG.info(" Other demo users: manager/Manager123!, cashier/Cashier123!, chef/Chef123!");
        LOG.info("=================================================================", DEMO_TENANT_CODE);

        seedKitchenAndInventory(mainAdmin, westAdmin, corr);
    }

    private void seedMenu(AccessPrincipal actor, CorrelationId corr) {
        var tax = menuService.createTax(actor, new CreateTaxRequest("VAT8", "Sales Tax 8%", new BigDecimal("8.00"), false, true), corr);

        var tempGroup = menuService.createModifierGroup(actor,
                new SaveModifierGroupRequest("TEMP", "Cooking Temp", 0, 1, false, true), corr);
        menuService.createModifier(actor, tempGroup.id(), new SaveModifierRequest("MED_RARE", "Medium Rare", BigDecimal.ZERO, 1, false, true), corr);
        menuService.createModifier(actor, tempGroup.id(), new SaveModifierRequest("MEDIUM", "Medium", BigDecimal.ZERO, 2, true, true), corr);
        menuService.createModifier(actor, tempGroup.id(), new SaveModifierRequest("WELL", "Well Done", BigDecimal.ZERO, 3, false, true), corr);

        var extrasGroup = menuService.createModifierGroup(actor,
                new SaveModifierGroupRequest("EXTRAS", "Burger Add-Ons", 0, 3, false, true), corr);
        var extraCheese = menuService.createModifier(actor, extrasGroup.id(), new SaveModifierRequest("X_CHZ", "Extra Cheddar", new BigDecimal("1.50"), 1, false, true), corr);
        menuService.createModifier(actor, extrasGroup.id(), new SaveModifierRequest("X_BACON", "Crispy Bacon", new BigDecimal("2.50"), 2, false, true), corr);
        menuService.createModifier(actor, extrasGroup.id(), new SaveModifierRequest("X_AIOLI", "Truffle Aioli", new BigDecimal("1.00"), 3, false, true), corr);

        var sizeGroup = menuService.createModifierGroup(actor,
                new SaveModifierGroupRequest("SIZE", "Drink Size", 0, 1, false, true), corr);
        menuService.createModifier(actor, sizeGroup.id(), new SaveModifierRequest("REG", "Regular", BigDecimal.ZERO, 1, true, true), corr);
        menuService.createModifier(actor, sizeGroup.id(), new SaveModifierRequest("LRG", "Large", new BigDecimal("1.50"), 2, false, true), corr);

        var burgersCat = menuService.createCategory(actor, new SaveCategoryRequest(null, "BURGERS", "Burgers", 1, "#D97706", true), corr);
        var mainsCat = menuService.createCategory(actor, new SaveCategoryRequest(null, "MAINS", "Mains & Grill", 2, "#DC2626", true), corr);
        var sidesCat = menuService.createCategory(actor, new SaveCategoryRequest(null, "SIDES", "Sides & Salads", 3, "#16A34A", true), corr);
        var drinksCat = menuService.createCategory(actor, new SaveCategoryRequest(null, "DRINKS", "Beverages", 4, "#2563EB", true), corr);
        var dessertsCat = menuService.createCategory(actor, new SaveCategoryRequest(null, "DESSERTS", "Desserts", 5, "#9333EA", true), corr);

        var classicBurger = menuService.createProduct(actor, new SaveProductRequest(
                burgersCat.id(), tax.id(), "BRG-CLS", "100001", "Classic Smash Burger",
                "Double seared Angus patty, aged cheddar, house pickles", new BigDecimal("12.50"),
                "FIXED", "STANDARD", "classpath:/images/menu/brg-cls.png", 1, true, List.of(tempGroup.id(), extrasGroup.id())), corr);
        menuService.createProduct(actor, new SaveProductRequest(
                burgersCat.id(), tax.id(), "BRG-TRUFF", "100002", "Truffle Mushroom Burger",
                "Roasted mushrooms, Swiss cheese, truffle aioli", new BigDecimal("15.50"),
                "FIXED", "STANDARD", "classpath:/images/menu/brg-truff.png", 2, true, List.of(tempGroup.id(), extrasGroup.id())), corr);
        menuService.createProduct(actor, new SaveProductRequest(
                mainsCat.id(), tax.id(), "MAIN-STK", "100003", "Grilled Ribeye Steak",
                "12oz prime ribeye with herb butter", new BigDecimal("28.00"),
                "FIXED", "STANDARD", "classpath:/images/menu/main-stk.png", 3, true, List.of(tempGroup.id())), corr);
        menuService.createProduct(actor, new SaveProductRequest(
                mainsCat.id(), tax.id(), "MAIN-SAL", "100004", "Herb Crusted Salmon",
                "Pan-seared Atlantic salmon with lemon caper sauce", new BigDecimal("22.00"),
                "FIXED", "STANDARD", "classpath:/images/menu/main-sal.png", 4, true, List.of()), corr);
        menuService.createProduct(actor, new SaveProductRequest(
                sidesCat.id(), tax.id(), "SIDE-FRI", "100005", "Crispy Sea-Salt Fries",
                "Hand-cut golden fries", new BigDecimal("5.50"),
                "FIXED", "STANDARD", "classpath:/images/menu/side-fri.png", 5, true, List.of(extrasGroup.id())), corr);
        menuService.createProduct(actor, new SaveProductRequest(
                sidesCat.id(), tax.id(), "SIDE-CAES", "100006", "Caesar Salad",
                "Crisp romaine, shaved parmesan, garlic croutons", new BigDecimal("8.50"),
                "FIXED", "STANDARD", "classpath:/images/menu/side-caes.png", 6, true, List.of()), corr);
        menuService.createProduct(actor, new SaveProductRequest(
                drinksCat.id(), tax.id(), "DRK-COLA", "100007", "Craft Cola",
                "Chilled cane-sugar craft cola", new BigDecimal("3.50"),
                "FIXED", "STANDARD", "classpath:/images/menu/drk-cola.png", 7, true, List.of(sizeGroup.id())), corr);
        menuService.createProduct(actor, new SaveProductRequest(
                drinksCat.id(), tax.id(), "DRK-LMN", "100008", "Fresh Mint Lemonade",
                "Pressed lemon juice with garden mint", new BigDecimal("4.50"),
                "FIXED", "STANDARD", "classpath:/images/menu/drk-lmn.png", 8, true, List.of(sizeGroup.id())), corr);
        menuService.createProduct(actor, new SaveProductRequest(
                dessertsCat.id(), tax.id(), "DES-LAVA", "100009", "Warm Chocolate Lava Cake",
                "Molten dark chocolate cake with vanilla bean cream", new BigDecimal("7.50"),
                "FIXED", "STANDARD", "classpath:/images/menu/des-lava.png", 9, true, List.of()), corr);

        // Kitchen routing rules after menu categories exist
        var hotStation = kitchenConfigService.createStation(actor, new SaveKitchenStationRequest(
                "HOT-GRILL", "Hot Kitchen & Grill", "HOT_KITCHEN", null, 1, 12, true, 42, "BILINGUAL", "UTF-8", true, true), corr);
        var coldStation = kitchenConfigService.createStation(actor, new SaveKitchenStationRequest(
                "COLD-BAR", "Cold Prep & Desserts", "COLD_KITCHEN", null, 2, 7, true, 42, "BILINGUAL", "UTF-8", false, true), corr);
        var bevStation = kitchenConfigService.createStation(actor, new SaveKitchenStationRequest(
                "BEV-BAR", "Beverage Bar", "BEVERAGE", null, 3, 4, false, 42, "BILINGUAL", "UTF-8", false, true), corr);

        kitchenConfigService.createRoutingRule(actor, new SaveKitchenRoutingRuleRequest(hotStation.id(), null, burgersCat.id(), 10, null, true), corr);
        kitchenConfigService.createRoutingRule(actor, new SaveKitchenRoutingRuleRequest(hotStation.id(), null, mainsCat.id(), 20, null, true), corr);
        kitchenConfigService.createRoutingRule(actor, new SaveKitchenRoutingRuleRequest(coldStation.id(), null, sidesCat.id(), 30, null, true), corr);
        kitchenConfigService.createRoutingRule(actor, new SaveKitchenRoutingRuleRequest(coldStation.id(), null, dessertsCat.id(), 40, null, true), corr);
        kitchenConfigService.createRoutingRule(actor, new SaveKitchenRoutingRuleRequest(bevStation.id(), null, drinksCat.id(), 50, null, true), corr);

        // Store IDs in thread-local/fields? Passing directly via helper record keeps state local!
        this.seededBurgerProductId = classicBurger.id();
        this.seededExtraCheeseModId = extraCheese.id();
    }

    private UUID seededBurgerProductId;
    private UUID seededExtraCheeseModId;

    private void seedTables(AccessPrincipal actor, CorrelationId corr) {
        var mainRoom = tableService.createArea(actor, new SaveDiningAreaRequest("MAIN_FLOOR", "Main Dining Room", 1, true), corr);
        var patio = tableService.createArea(actor, new SaveDiningAreaRequest("PATIO", "Outdoor Patio", 2, true), corr);

        tableService.createTable(actor, new SaveDiningTableRequest(mainRoom.id(), "T1", "Table 1", 2, 1, true), corr);
        tableService.createTable(actor, new SaveDiningTableRequest(mainRoom.id(), "T2", "Table 2", 4, 2, true), corr);
        tableService.createTable(actor, new SaveDiningTableRequest(mainRoom.id(), "T3", "Table 3", 4, 3, true), corr);
        tableService.createTable(actor, new SaveDiningTableRequest(mainRoom.id(), "T4", "Table 4", 6, 4, true), corr);
        tableService.createTable(actor, new SaveDiningTableRequest(patio.id(), "P1", "Patio 1", 2, 5, true), corr);
        tableService.createTable(actor, new SaveDiningTableRequest(patio.id(), "P2", "Patio 2", 4, 6, true), corr);
    }

    private void seedKitchenAndInventory(AccessPrincipal mainAdmin, AccessPrincipal westAdmin, CorrelationId corr) {
        var gram = inventoryCatalogService.createUnit(mainAdmin, new SaveUnitRequest("G", "Gram", "WEIGHT", null, BigDecimal.ONE), corr);
        var kg = inventoryCatalogService.createUnit(mainAdmin, new SaveUnitRequest("KG", "Kilogram", "WEIGHT", gram.id(), new BigDecimal("1000")), corr);
        var ml = inventoryCatalogService.createUnit(mainAdmin, new SaveUnitRequest("ML", "Milliliter", "VOLUME", null, BigDecimal.ONE), corr);
        var liter = inventoryCatalogService.createUnit(mainAdmin, new SaveUnitRequest("L", "Liter", "VOLUME", ml.id(), new BigDecimal("1000")), corr);
        var pcs = inventoryCatalogService.createUnit(mainAdmin, new SaveUnitRequest("PCS", "Piece", "COUNT", null, BigDecimal.ONE), corr);

        var proteinCat = inventoryCatalogService.createCategory(mainAdmin,
                new SaveInventoryCategoryRequest("PROTEIN", "Proteins & Dairy", "Fresh patties, fillets, and cheeses", 1, true), corr);
        var produceCat = inventoryCatalogService.createCategory(mainAdmin,
                new SaveInventoryCategoryRequest("PRODUCE", "Bakery & Produce", "Buns, potatoes, and greens", 2, true), corr);
        var bevCat = inventoryCatalogService.createCategory(mainAdmin,
                new SaveInventoryCategoryRequest("BEV_STK", "Beverage Mixers", "Syrups and cold bar supplies", 3, true), corr);

        var mainCooler = inventoryCatalogService.createLocation(mainAdmin,
                new SaveInventoryLocationRequest("MAIN-COOLER", "Main Walk-In Cooler", "COLD_ROOM", true, true), corr);
        inventoryCatalogService.createLocation(westAdmin,
                new SaveInventoryLocationRequest("WEST-COOLER", "Westside Walk-In Cooler", "COLD_ROOM", true, true), corr);

        var beefPatty = inventoryCatalogService.createItem(mainAdmin, new SaveInventoryItemRequest(
                proteinCat.id(), mainCooler.id(), false, "ING-BEEF", "Angus Beef Patty", "Fresh 150g chuck patty",
                "RAW_INGREDIENT", null, pcs.id(), new BigDecimal("10"), new BigDecimal("20"),
                new BigDecimal("2.20"), "STANDARD", false, false, "ALLOW", "WEIGHTED_AVERAGE", true), corr);
        var bun = inventoryCatalogService.createItem(mainAdmin, new SaveInventoryItemRequest(
                produceCat.id(), mainCooler.id(), false, "ING-BUN", "Brioche Burger Bun", "Toasted brioche bun",
                "RAW_INGREDIENT", null, pcs.id(), new BigDecimal("12"), new BigDecimal("24"),
                new BigDecimal("0.65"), "STANDARD", false, false, "ALLOW", "WEIGHTED_AVERAGE", true), corr);
        var cheddar = inventoryCatalogService.createItem(mainAdmin, new SaveInventoryItemRequest(
                proteinCat.id(), mainCooler.id(), false, "ING-CHZ", "Aged Cheddar Slice", "Sharp cheddar slice",
                "RAW_INGREDIENT", null, pcs.id(), new BigDecimal("15"), new BigDecimal("30"),
                new BigDecimal("0.40"), "STANDARD", false, false, "ALLOW", "WEIGHTED_AVERAGE", true), corr);
        var fries = inventoryCatalogService.createItem(mainAdmin, new SaveInventoryItemRequest(
                produceCat.id(), mainCooler.id(), false, "ING-FRIES", "Cut Idaho Potatoes", "Prep-cut fries",
                "RAW_INGREDIENT", null, kg.id(), new BigDecimal("5"), new BigDecimal("10"),
                new BigDecimal("2.80"), "STANDARD", false, false, "ALLOW", "WEIGHTED_AVERAGE", true), corr);
        var salmon = inventoryCatalogService.createItem(mainAdmin, new SaveInventoryItemRequest(
                proteinCat.id(), mainCooler.id(), false, "ING-SALMON", "Atlantic Salmon Fillet", "Fresh 200g portion",
                "RAW_INGREDIENT", null, pcs.id(), new BigDecimal("5"), new BigDecimal("10"),
                new BigDecimal("6.50"), "STANDARD", false, false, "ALLOW", "WEIGHTED_AVERAGE", true), corr);
        var syrup = inventoryCatalogService.createItem(mainAdmin, new SaveInventoryItemRequest(
                bevCat.id(), mainCooler.id(), false, "ING-SYRUP", "Mint Citrus Base", "House lemonade concentrate",
                "RAW_INGREDIENT", null, liter.id(), new BigDecimal("2"), new BigDecimal("4"),
                new BigDecimal("4.00"), "STANDARD", false, false, "ALLOW", "WEIGHTED_AVERAGE", true), corr);

        stockLedgerService.recordAdjustment(mainAdmin, new RecordStockAdjustmentRequest(
                mainCooler.id(), beefPatty.id(), pcs.id(), new BigDecimal("60"), "OPENING_BALANCE",
                new BigDecimal("2.20"), "Demo opening balance", null, null, "demo-open-beef", null, false), corr);
        stockLedgerService.recordAdjustment(mainAdmin, new RecordStockAdjustmentRequest(
                mainCooler.id(), bun.id(), pcs.id(), new BigDecimal("80"), "OPENING_BALANCE",
                new BigDecimal("0.65"), "Demo opening balance", null, null, "demo-open-bun", null, false), corr);
        stockLedgerService.recordAdjustment(mainAdmin, new RecordStockAdjustmentRequest(
                mainCooler.id(), cheddar.id(), pcs.id(), new BigDecimal("100"), "OPENING_BALANCE",
                new BigDecimal("0.40"), "Demo opening balance", null, null, "demo-open-chz", null, false), corr);
        stockLedgerService.recordAdjustment(mainAdmin, new RecordStockAdjustmentRequest(
                mainCooler.id(), fries.id(), kg.id(), new BigDecimal("25"), "OPENING_BALANCE",
                new BigDecimal("2.80"), "Demo opening balance", null, null, "demo-open-fries", null, false), corr);
        stockLedgerService.recordAdjustment(mainAdmin, new RecordStockAdjustmentRequest(
                mainCooler.id(), salmon.id(), pcs.id(), new BigDecimal("20"), "OPENING_BALANCE",
                new BigDecimal("6.50"), "Demo opening balance", null, null, "demo-open-salmon", null, false), corr);
        stockLedgerService.recordAdjustment(mainAdmin, new RecordStockAdjustmentRequest(
                mainCooler.id(), syrup.id(), liter.id(), new BigDecimal("12"), "OPENING_BALANCE",
                new BigDecimal("4.00"), "Demo opening balance", null, null, "demo-open-syrup", null, false), corr);

        if (seededBurgerProductId != null) {
            List<SaveRecipeRequest.ModifierMappingInput> modMappings = seededExtraCheeseModId != null
                    ? List.of(new SaveRecipeRequest.ModifierMappingInput(
                            seededExtraCheeseModId, "ADD", cheddar.id(), null, pcs.id(), BigDecimal.ONE, null, "Extra cheddar slice"))
                    : List.of();
            recipeService.createOrNewVersion(mainAdmin, null, new SaveRecipeRequest(
                    seededBurgerProductId, null, false, "Classic Smash Burger Recipe", false,
                    BigDecimal.ONE, pcs.id(), BigDecimal.ZERO, "Sear double patty, melt cheddar, assemble on toasted bun",
                    8, true, true,
                    List.of(
                            new SaveRecipeRequest.IngredientLineInput(beefPatty.id(), null, pcs.id(), new BigDecimal("2"), BigDecimal.ZERO, false, null, "Double smash patty", 1),
                            new SaveRecipeRequest.IngredientLineInput(bun.id(), null, pcs.id(), BigDecimal.ONE, BigDecimal.ZERO, false, null, "Brioche bun", 2),
                            new SaveRecipeRequest.IngredientLineInput(cheddar.id(), null, pcs.id(), new BigDecimal("2"), BigDecimal.ZERO, false, null, "Two cheddar slices", 3)
                    ),
                    modMappings
            ), corr);
        }
    }

    private UserAccount createUser(
            TenantId tenantId,
            String username,
            String fullName,
            String email,
            String rawPassword,
            String roleCode,
            Set<UUID> branchIds
    ) {
        UUID userId = UUID.randomUUID();
        UserAccount saved = userRepo.save(new UserAccount(
                userId, tenantId, username, fullName, email,
                passwordHasher.hash(rawPassword), true, 0, null, null, 0L
        ));
        userRepo.replaceBranchAccess(tenantId, userId, branchIds.stream().map(BranchId::of).collect(java.util.stream.Collectors.toSet()));
        RoleDefinition role = roleRepo.findByCodeAndTenant(roleCode, tenantId).orElseThrow();
        userRepo.replaceRoleAssignments(tenantId, userId, List.of(
                new UserRoleAssignment(UUID.randomUUID(), tenantId, userId, role.id(), role.code(), role.name(), null)
        ));
        return saved;
    }
}
