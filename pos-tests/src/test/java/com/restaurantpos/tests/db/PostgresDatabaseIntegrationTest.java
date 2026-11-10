package com.restaurantpos.tests.db;

import com.restaurantpos.domain.exception.ConcurrentModificationDomainException;
import com.restaurantpos.domain.exception.DataIntegrityDomainException;
import com.restaurantpos.domain.exception.DuplicateResourceException;
import com.restaurantpos.domain.inventory.StockBalanceView;
import com.restaurantpos.domain.menu.ProductCatalogView;
import com.restaurantpos.domain.order.OrderItemSnapshot;
import com.restaurantpos.domain.order.OrderRecord;
import com.restaurantpos.domain.order.OrderStatus;
import com.restaurantpos.domain.order.OrderSummary;
import com.restaurantpos.domain.order.OrderType;
import com.restaurantpos.domain.payment.PaymentRecord;
import com.restaurantpos.domain.port.AuditLogPort;
import com.restaurantpos.domain.port.AuditLogQueryPort;
import com.restaurantpos.domain.port.BranchRepositoryPort;
import com.restaurantpos.domain.port.OrderRepositoryPort;
import com.restaurantpos.domain.port.PaymentRepositoryPort;
import com.restaurantpos.domain.port.ProductQueryPort;
import com.restaurantpos.domain.port.StockBalanceRepositoryPort;
import com.restaurantpos.domain.port.TenantRepositoryPort;
import com.restaurantpos.domain.security.AuditEntry;
import com.restaurantpos.domain.tenancy.Branch;
import com.restaurantpos.domain.tenancy.Tenant;
import com.restaurantpos.infrastructure.persistence.entity.BranchProductOverrideEntity;
import com.restaurantpos.infrastructure.persistence.entity.CategoryEntity;
import com.restaurantpos.infrastructure.persistence.entity.IngredientEntity;
import com.restaurantpos.infrastructure.persistence.entity.PaymentMethodEntity;
import com.restaurantpos.infrastructure.persistence.entity.ProductEntity;
import com.restaurantpos.infrastructure.persistence.entity.StockLocationEntity;
import com.restaurantpos.infrastructure.persistence.entity.StockMovementEntity;
import com.restaurantpos.infrastructure.persistence.entity.UnitEntity;
import com.restaurantpos.infrastructure.persistence.entity.UserEntity;
import com.restaurantpos.infrastructure.persistence.repository.BranchProductOverrideJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.CategoryJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.IngredientJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.PaymentMethodJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.ProductJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.StockLocationJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.StockMovementJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.UnitJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.UserJpaRepository;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.shared.security.AuditAction;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end PostgreSQL verification covering Flyway V001..V008 migrations, Hibernate ddl-auto=validate,
 * FK/unique constraints, tenant/branch isolation, optimistic locking, transaction rollback,
 * audit persistence, DTO projections, and append-only ledger immutability.
 */
@SpringBootTest(
        classes = PostgresTestApplication.class,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.open-in-view=false",
                "spring.flyway.enabled=true",
                "spring.flyway.locations=classpath:db/migration"
        }
)
class PostgresDatabaseIntegrationTest {

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
                    .withDatabaseName("restaurant_pos_test")
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

    @Autowired private Flyway flyway;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private TenantRepositoryPort tenantRepo;
    @Autowired private BranchRepositoryPort branchRepo;
    @Autowired private OrderRepositoryPort orderRepo;
    @Autowired private ProductQueryPort productQueryPort;
    @Autowired private PaymentRepositoryPort paymentRepo;
    @Autowired private StockBalanceRepositoryPort stockBalanceRepo;
    @Autowired private AuditLogPort auditLogPort;
    @Autowired private AuditLogQueryPort auditLogQueryPort;

    @Autowired private UserJpaRepository userJpaRepo;
    @Autowired private CategoryJpaRepository categoryJpaRepo;
    @Autowired private ProductJpaRepository productJpaRepo;
    @Autowired private BranchProductOverrideJpaRepository overrideJpaRepo;
    @Autowired private PaymentMethodJpaRepository paymentMethodJpaRepo;
    @Autowired private UnitJpaRepository unitJpaRepo;
    @Autowired private IngredientJpaRepository ingredientJpaRepo;
    @Autowired private StockLocationJpaRepository stockLocationJpaRepo;
    @Autowired private StockMovementJpaRepository stockMovementJpaRepo;

    @Test
    void executesAllFlywayMigrationsAgainstPostgreSql() {
        var applied = flyway.info().applied();
        assertEquals(13, applied.length, "Expected all V001..V013 Flyway migrations to be applied");
    }

    @Test
    void enforcesForeignKeysAndUniqueConstraints() {
        UUID missingTenantId = UUID.randomUUID();
        Branch orphanBranch = new Branch(
                UUID.randomUUID(),
                TenantId.of(missingTenantId),
                "ORPHAN",
                "Orphan Branch",
                "UTC",
                "USD",
                true,
                0L
        );
        assertThrows(DataIntegrityDomainException.class, () -> branchRepo.save(orphanBranch));

        String uniqueCode = "TEN-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant first = new Tenant(UUID.randomUUID(), uniqueCode, "Legal Co", "Display Co", "USD", "ACTIVE", 0L);
        tenantRepo.save(first);

        Tenant duplicateCode = new Tenant(UUID.randomUUID(), uniqueCode, "Other Co", "Other", "USD", "ACTIVE", 0L);
        assertThrows(DuplicateResourceException.class, () -> tenantRepo.save(duplicateCode));
    }

    @Test
    void enforcesTenantBranchIsolationHistoricalSnapshotsOptimisticLockingAndAuditImmutability() {
        UUID tenantAId = UUID.randomUUID();
        UUID tenantBId = UUID.randomUUID();
        Tenant tenantA = tenantRepo.save(new Tenant(tenantAId, "TA-" + tenantAId.toString().substring(0, 6), "Tenant A LLC", "Tenant A", "USD", "ACTIVE", 0L));
        tenantRepo.save(new Tenant(tenantBId, "TB-" + tenantBId.toString().substring(0, 6), "Tenant B LLC", "Tenant B", "USD", "ACTIVE", 0L));

        UUID branchA1Id = UUID.randomUUID();
        UUID branchA2Id = UUID.randomUUID();
        branchRepo.save(new Branch(branchA1Id, TenantId.of(tenantA.id()), "BR1", "Downtown", "UTC", "USD", true, 0L));
        branchRepo.save(new Branch(branchA2Id, TenantId.of(tenantA.id()), "BR2", "Airport", "UTC", "USD", true, 0L));

        // Isolation check: Tenant B cannot access Tenant A's branch
        assertTrue(branchRepo.findByIdAndTenant(BranchId.of(branchA1Id), TenantId.of(tenantBId)).isEmpty());

        UUID userId = UUID.randomUUID();
        userJpaRepo.saveAndFlush(new UserEntity(userId, tenantAId, "cashier_a", "Alice", "alice@test.local", "bcrypt-placeholder"));

        // Menu + branch override projection check
        UUID catId = UUID.randomUUID();
        categoryJpaRepo.saveAndFlush(new CategoryEntity(catId, tenantAId, "BURGERS", "Burgers", 1, true));
        UUID prodId = UUID.randomUUID();
        ProductEntity product = productJpaRepo.saveAndFlush(new ProductEntity(prodId, tenantAId, catId, "SKU-BGR-1", "Classic Burger", new BigDecimal("10.0000")));
        overrideJpaRepo.saveAndFlush(new BranchProductOverrideEntity(UUID.randomUUID(), tenantAId, branchA1Id, prodId, new BigDecimal("12.5000"), true));

        List<ProductCatalogView> catalogA1 = productQueryPort.findBranchCatalog(TenantId.of(tenantAId), BranchId.of(branchA1Id), 0, 20);
        assertEquals(1, catalogA1.size());
        assertEquals(0, new BigDecimal("12.5000").compareTo(catalogA1.get(0).effectivePrice()));

        List<ProductCatalogView> catalogA2 = productQueryPort.findBranchCatalog(TenantId.of(tenantAId), BranchId.of(branchA2Id), 0, 20);
        assertEquals(0, new BigDecimal("10.0000").compareTo(catalogA2.get(0).effectivePrice()));

        // Create order with immutable snapshot
        UUID orderId = UUID.randomUUID();
        OrderItemSnapshot item = new OrderItemSnapshot(
                UUID.randomUUID(), prodId, "SKU-BGR-1", "Classic Burger", null,
                new BigDecimal("2.0000"), new BigDecimal("12.5000"),
                new BigDecimal("10.0000"), new BigDecimal("2.5000"),
                BigDecimal.ZERO, new BigDecimal("27.5000"), 0L
        );
        OrderRecord created = orderRepo.save(new OrderRecord(
                orderId, TenantId.of(tenantAId), BranchId.of(branchA1Id), userId,
                "ORD-1001", OrderType.DINE_IN, OrderStatus.OPEN, "USD",
                new BigDecimal("25.0000"), BigDecimal.ZERO, new BigDecimal("2.5000"),
                new BigDecimal("27.5000"), BigDecimal.ZERO, 2, Instant.now(), 0L, List.of(item)
        ));

        // Isolation: Branch A2 cannot read Branch A1's order
        assertTrue(orderRepo.findByIdScoped(orderId, TenantId.of(tenantAId), BranchId.of(branchA2Id)).isEmpty());

        // Paginated summary projection works without loading items
        List<OrderSummary> summaries = orderRepo.findSummariesByBranch(TenantId.of(tenantAId), BranchId.of(branchA1Id), 0, 10);
        assertEquals(1, summaries.size());
        assertEquals("ORD-1001", summaries.get(0).orderNumber());

        // Historical integrity: mutating catalog price/name does NOT change persisted order_items snapshot
        product.setName("Renamed Burger");
        product.setBasePrice(new BigDecimal("99.0000"));
        productJpaRepo.saveAndFlush(product);
        OrderRecord reloadedAfterMenuChange = orderRepo.findByIdScoped(orderId, TenantId.of(tenantAId), BranchId.of(branchA1Id)).orElseThrow();
        assertEquals("Classic Burger", reloadedAfterMenuChange.items().get(0).productNameSnapshot());
        assertEquals(0, new BigDecimal("12.5000").compareTo(reloadedAfterMenuChange.items().get(0).unitPriceSnapshot()));

        // Optimistic locking: update advances version to 1; saving stale version 0 fails
        OrderRecord updated = orderRepo.save(reloadedAfterMenuChange.withStatus(OrderStatus.IN_KITCHEN));
        assertEquals(1L, updated.version());
        assertThrows(ConcurrentModificationDomainException.class, () ->
                orderRepo.save(reloadedAfterMenuChange.withStatus(OrderStatus.READY)));

        // Append-only payments + idempotency constraint + trigger immutability
        UUID pmId = UUID.randomUUID();
        paymentMethodJpaRepo.saveAndFlush(new PaymentMethodEntity(pmId, tenantAId, "CASH", "Cash", "CASH", true));
        UUID paymentId = UUID.randomUUID();
        PaymentRecord payment = new PaymentRecord(
                paymentId, TenantId.of(tenantAId), BranchId.of(branchA1Id), orderId, pmId, userId,
                "idem-pay-1", "USD", new BigDecimal("27.5000"), new BigDecimal("30.0000"),
                new BigDecimal("2.5000"), BigDecimal.ZERO, "SETTLED", Instant.now()
        );
        paymentRepo.append(payment);
        assertThrows(DuplicateResourceException.class, () ->
                paymentRepo.append(new PaymentRecord(
                        UUID.randomUUID(), TenantId.of(tenantAId), BranchId.of(branchA1Id), orderId, pmId, userId,
                        "idem-pay-1", "USD", new BigDecimal("27.5000"), new BigDecimal("30.0000"),
                        new BigDecimal("2.5000"), BigDecimal.ZERO, "SETTLED", Instant.now()
                )));
        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("UPDATE payments SET amount = 1.00 WHERE id = ?", paymentId));
        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("DELETE FROM payments WHERE id = ?", paymentId));

        // Audit persistence + redaction + immutability
        UUID auditId = UUID.randomUUID();
        auditLogPort.record(new AuditEntry(
                auditId,
                TenantId.of(tenantAId),
                BranchId.of(branchA1Id),
                userId,
                AuditAction.REFUND_ISSUED,
                "Order",
                orderId.toString(),
                "{\"pin\":\"4321\",\"reason\":\"overcharge\"}",
                CorrelationId.of("corr-db-test"),
                Instant.now()
        ));
        List<AuditEntry> audits = auditLogQueryPort.findRecentByBranch(TenantId.of(tenantAId), BranchId.of(branchA1Id), 0, 10);
        assertFalse(audits.isEmpty());
        assertFalse(audits.get(0).detailsJson().contains("4321"));
        assertTrue(audits.get(0).detailsJson().contains("overcharge"));
        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("DELETE FROM audit_logs WHERE id = ?", auditId));
    }

    @Test
    void rollsBackTransactionAtomicallyAndEnforcesStockOptimisticLocking() {
        UUID tenantId = UUID.randomUUID();
        tenantRepo.save(new Tenant(tenantId, "TR-" + tenantId.toString().substring(0, 6), "Rollback LLC", "Rollback", "USD", "ACTIVE", 0L));
        UUID branchId = UUID.randomUUID();
        branchRepo.save(new Branch(branchId, TenantId.of(tenantId), "BR-R", "Main", "UTC", "USD", true, 0L));
        UUID userId = UUID.randomUUID();
        userJpaRepo.saveAndFlush(new UserEntity(userId, tenantId, "mgr_r", "Manager", null, "hash"));

        // Transaction rollback test: exception inside transaction rolls back audit log write
        UUID rolledBackAuditId = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            auditLogPort.record(new AuditEntry(
                    rolledBackAuditId,
                    TenantId.of(tenantId),
                    BranchId.of(branchId),
                    userId,
                    AuditAction.SETTINGS_CHANGED,
                    "Branch",
                    branchId.toString(),
                    "{\"setting\":\"tax\"}",
                    CorrelationId.of("corr-rollback"),
                    Instant.now()
            ));
            throw new IllegalStateException("Simulated failure forcing rollback");
        }));
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE id = ?", Integer.class, rolledBackAuditId);
        assertNotNull(count);
        assertEquals(0, count);

        // Inventory ledger immutability + stock balance optimistic locking
        UUID unitId = UUID.randomUUID();
        unitJpaRepo.saveAndFlush(new UnitEntity(unitId, tenantId, "KG", "Kilogram", "WEIGHT"));
        UUID ingId = UUID.randomUUID();
        ingredientJpaRepo.saveAndFlush(new IngredientEntity(ingId, tenantId, "ING-BEEF", "Ground Beef", unitId));
        UUID locId = UUID.randomUUID();
        stockLocationJpaRepo.saveAndFlush(new StockLocationEntity(locId, tenantId, branchId, "WALKIN", "Walk-In Cooler", true));

        UUID movementId = UUID.randomUUID();
        stockMovementJpaRepo.saveAndFlush(new StockMovementEntity(
                movementId, tenantId, branchId, locId, ingId, "OPENING_STOCK",
                new BigDecimal("50.0000"), new BigDecimal("8.0000"), "open-stock-1", Instant.now()
        ));
        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("UPDATE stock_movements SET quantity_delta = 99 WHERE id = ?", movementId));

        StockBalanceView initial = stockBalanceRepo.save(new StockBalanceView(
                UUID.randomUUID(), TenantId.of(tenantId), BranchId.of(branchId), locId, ingId,
                new BigDecimal("50.0000"), 0L
        ));
        StockBalanceView updated = stockBalanceRepo.save(new StockBalanceView(
                initial.id(), initial.tenantId(), initial.branchId(), initial.stockLocationId(),
                initial.ingredientId(), new BigDecimal("48.0000"), initial.version()
        ));
        assertEquals(1L, updated.version());
        assertThrows(ConcurrentModificationDomainException.class, () -> stockBalanceRepo.save(
                new StockBalanceView(initial.id(), initial.tenantId(), initial.branchId(),
                        initial.stockLocationId(), initial.ingredientId(), new BigDecimal("47.0000"), 0L)));
    }
}
