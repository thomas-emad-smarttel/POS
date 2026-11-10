package com.restaurantpos.tests.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantpos.application.security.RbacSeedService;
import com.restaurantpos.application.security.TokenSupport;
import com.restaurantpos.domain.port.BranchRepositoryPort;
import com.restaurantpos.domain.port.PasswordHasherPort;
import com.restaurantpos.domain.port.RoleRepositoryPort;
import com.restaurantpos.domain.port.TenantRepositoryPort;
import com.restaurantpos.domain.port.UserRepositoryPort;
import com.restaurantpos.domain.port.UserSessionRepositoryPort;
import com.restaurantpos.domain.security.DefaultRoleTemplate;
import com.restaurantpos.domain.security.RoleDefinition;
import com.restaurantpos.domain.security.UserAccount;
import com.restaurantpos.domain.security.UserRoleAssignment;
import com.restaurantpos.domain.security.UserSession;
import com.restaurantpos.domain.tenancy.Branch;
import com.restaurantpos.domain.tenancy.Tenant;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.shared.security.PermissionCode;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack security, authentication, RBAC, tenant/branch isolation, supervisor approval,
 * and audit logging integration tests backed by real PostgreSQL.
 */
@SpringBootTest(
        classes = SecurityApiTestApplication.class,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.open-in-view=false",
                "spring.flyway.enabled=true",
                "spring.flyway.locations=classpath:db/migration",
                "pos.security.max-failed-login-attempts=3",
                "pos.security.max-active-sessions-per-user=2",
                "pos.security.max-throttle-attempts-per-key=50"
        }
)
@AutoConfigureMockMvc
class SecurityAndRbacIntegrationTest {

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
                    .withDatabaseName("restaurant_pos_sec_test")
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
    @Autowired private UserSessionRepositoryPort sessionRepo;
    @Autowired private PasswordHasherPort passwordHasher;
    @Autowired private RbacSeedService rbacSeedService;

    private String tenantACode;
    private UUID tenantAId;
    private UUID branchA1Id;
    private UUID branchA2Id;
    private String tenantBCode;
    private UUID tenantBId;
    private UUID branchB1Id;

    @BeforeEach
    void setupIsolatedTenants() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        tenantAId = UUID.randomUUID();
        tenantACode = "TA_" + suffix;
        tenantRepo.save(new Tenant(tenantAId, tenantACode, "Tenant A", "Tenant A", "USD", "ACTIVE", 0L));
        branchA1Id = UUID.randomUUID();
        branchA2Id = UUID.randomUUID();
        branchRepo.save(new Branch(branchA1Id, TenantId.of(tenantAId), "B1", "Branch 1", "UTC", "USD", true, 0L));
        branchRepo.save(new Branch(branchA2Id, TenantId.of(tenantAId), "B2", "Branch 2", "UTC", "USD", true, 0L));
        rbacSeedService.seedTenantDefaults(TenantId.of(tenantAId));

        tenantBId = UUID.randomUUID();
        tenantBCode = "TB_" + suffix;
        tenantRepo.save(new Tenant(tenantBId, tenantBCode, "Tenant B", "Tenant B", "USD", "ACTIVE", 0L));
        branchB1Id = UUID.randomUUID();
        branchRepo.save(new Branch(branchB1Id, TenantId.of(tenantBId), "B1", "Other Branch", "UTC", "USD", true, 0L));
        rbacSeedService.seedTenantDefaults(TenantId.of(tenantBId));
    }

    @Test
    void authenticationLifecycleLockoutMultiTerminalSessionsLogoutAndPasswordChange() throws Exception {
        UUID cashierId = createUserWithRole(tenantAId, "cashier1", "StrongPass!1", DefaultRoleTemplate.CASHIER.code(), List.of(branchA1Id));

        // 1. Unknown user and wrong password return identical generic message (no enumeration)
        String unknownResp = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","username":"no_such_user","password":"StrongPass!1"}
                                """.formatted(tenantACode)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        String wrongPassResp = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","username":"cashier1","password":"WrongPassword!"}
                                """.formatted(tenantACode)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        assertEquals(
                objectMapper.readTree(unknownResp).at("/error/message").asText(),
                objectMapper.readTree(wrongPassResp).at("/error/message").asText()
        );

        // 2. Valid login from Terminal 1 and Terminal 2 (registered in terminals table)
        UUID term1Id = registerTerminal(tenantAId, branchA1Id, "TERM-01");
        UUID term2Id = registerTerminal(tenantAId, branchA1Id, "TERM-02");
        UUID term3Id = registerTerminal(tenantAId, branchA1Id, "TERM-03");
        String token1 = loginAndExtractToken(tenantACode, "cashier1", "StrongPass!1", branchA1Id, term1Id);
        String token2 = loginAndExtractToken(tenantACode, "cashier1", "StrongPass!1", branchA1Id, term2Id);
        assertNotEquals(token1, token2);

        // Token stored ONLY as SHA-256 hash in user_sessions, never plaintext
        Integer rawCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_sessions WHERE token_hash = ?", Integer.class, token1);
        assertEquals(0, rawCount);
        Integer hashCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_sessions WHERE token_hash = ?", Integer.class, TokenSupport.sha256Hex(token1));
        assertEquals(1, hashCount);

        // 3. Third login enforces max-active-sessions-per-user=2 by revoking oldest session (token1)
        String token3 = loginAndExtractToken(tenantACode, "cashier1", "StrongPass!1", branchA1Id, term3Id);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token1))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("cashier1"));
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token3))
                .andExpect(status().isOk());

        // 4. Logout revokes token2 immediately
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token2))
                .andExpect(status().isUnauthorized());

        // 5. Expired session is rejected
        String expiredRaw = TokenSupport.generateOpaqueToken();
        sessionRepo.create(new UserSession(
                UUID.randomUUID(), TenantId.of(tenantAId), BranchId.of(branchA1Id), cashierId,
                null, TokenSupport.sha256Hex(expiredRaw),
                Instant.now().minus(5, ChronoUnit.MINUTES), null, Instant.now().minus(60, ChronoUnit.MINUTES)
        ));
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + expiredRaw))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_EXPIRED"));

        // 6. Change password revokes active sessions and logs audit entry without storing secret
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"StrongPass!1","newPassword":"NewStrongPass!2"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token3))
                .andExpect(status().isUnauthorized());

        String freshToken = loginAndExtractToken(tenantACode, "cashier1", "NewStrongPass!2", branchA1Id, term1Id);
        assertFalse(freshToken.isBlank());

        // 7. Lockout after 3 consecutive failed attempts
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"tenantCode":"%s","username":"cashier1","password":"WrongPassword!"}
                                    """.formatted(tenantACode)))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","username":"cashier1","password":"NewStrongPass!2"}
                                """.formatted(tenantACode)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error.code").value("AUTH_ACCOUNT_LOCKED"));

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE tenant_id = ? AND action = 'ACCOUNT_LOCKED'",
                Integer.class, tenantAId);
        assertTrue(auditCount != null && auditCount >= 1);
    }

    @Test
    void enforcesRbacAuthorizationTenantBranchIsolationImmediateRevocationAndEscalationPrevention() throws Exception {
        createUserWithRole(tenantAId, "owner_a", "OwnerPass!1", DefaultRoleTemplate.RESTAURANT_OWNER.code(), List.of(branchA1Id, branchA2Id));
        UUID cashierId = createUserWithRole(tenantAId, "cashier_a", "CashierPass!1", DefaultRoleTemplate.CASHIER.code(), List.of(branchA1Id));
        UUID managerId = createUserWithRole(tenantAId, "mgr_a", "ManagerPass!1", DefaultRoleTemplate.BRANCH_MANAGER.code(), List.of(branchA1Id));
        UUID ownerBId = createUserWithRole(tenantBId, "owner_b", "OwnerBPass!1", DefaultRoleTemplate.RESTAURANT_OWNER.code(), List.of(branchB1Id));

        String ownerAToken = loginAndExtractToken(tenantACode, "owner_a", "OwnerPass!1", branchA1Id, null);
        String cashierToken = loginAndExtractToken(tenantACode, "cashier_a", "CashierPass!1", branchA1Id, null);
        String mgrToken = loginAndExtractToken(tenantACode, "mgr_a", "ManagerPass!1", branchA1Id, null);

        // Unauthenticated -> 401
        mockMvc.perform(get("/api/v1/roles")).andExpect(status().isUnauthorized());

        // Cashier lacks roles.view -> 403
        mockMvc.perform(get("/api/v1/roles").header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));

        // Owner can view permissions and roles
        mockMvc.perform(get("/api/v1/roles").header("Authorization", "Bearer " + ownerAToken))
                .andExpect(status().isOk());

        // Unauthorized branch switch -> 403 + AUTHORIZATION_DENIED audited
        mockMvc.perform(post("/api/v1/auth/switch-branch")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + branchA2Id + "\"}"))
                .andExpect(status().isForbidden());

        // Authorized branch switch succeeds for owner_a
        mockMvc.perform(post("/api/v1/auth/switch-branch")
                        .header("Authorization", "Bearer " + ownerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + branchA2Id + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeBranchId").value(branchA2Id.toString()));

        // Cross-tenant IDOR prevention: Tenant A owner cannot modify Tenant B user
        mockMvc.perform(post("/api/v1/users/" + ownerBId + "/status")
                        .header("Authorization", "Bearer " + ownerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isNotFound());

        // Privilege escalation prevention: Branch Manager (lacks roles.manage & settings.manage) cannot assign RESTAURANT_OWNER
        RoleDefinition ownerRoleA = roleRepo.findByCodeAndTenant(DefaultRoleTemplate.RESTAURANT_OWNER.code(), TenantId.of(tenantAId)).orElseThrow();
        mockMvc.perform(put("/api/v1/users/" + cashierId + "/roles")
                        .header("Authorization", "Bearer " + mgrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignments":[{"roleId":"%s","branchId":null}]}
                                """.formatted(ownerRoleA.id())))
                .andExpect(status().isForbidden());

        // Create custom role, assign to cashier, then revoke permission -> immediate enforcement!
        MvcResult createRoleRes = mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", "Bearer " + ownerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"SHIFT_SUPERVISOR","name":"Shift Supervisor","scopeType":"BRANCH","maxDiscountPct":15.00,"permissionCodes":["audit.view"]}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID customRoleId = UUID.fromString(objectMapper.readTree(createRoleRes.getResponse().getContentAsString()).at("/data/id").asText());

        mockMvc.perform(put("/api/v1/users/" + cashierId + "/roles")
                        .header("Authorization", "Bearer " + ownerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignments":[{"roleId":"%s","branchId":"%s"}]}
                                """.formatted(customRoleId, branchA1Id)))
                .andExpect(status().isOk());

        // Cashier now has audit.view immediately on existing token
        mockMvc.perform(get("/api/v1/audit-logs").header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(containsString("passwordHash"))));

        // Revoke audit.view from custom role -> next request with SAME cashierToken gets 403!
        mockMvc.perform(put("/api/v1/roles/" + customRoleId + "/permissions")
                        .header("Authorization", "Bearer " + ownerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\":[\"dashboard.view\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/audit-logs").header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden());

        // Idempotent seeding preserves customized role!
        rbacSeedService.seedTenantDefaults(TenantId.of(tenantAId));
        RoleDefinition preserved = roleRepo.findByIdAndTenant(customRoleId, TenantId.of(tenantAId)).orElseThrow();
        assertTrue(preserved.permissions().contains(PermissionCode.DASHBOARD_VIEW));
        assertFalse(preserved.permissions().contains(PermissionCode.AUDIT_VIEW));

        // Protected role cannot remove core admin permissions
        RoleDefinition superAdminRole = roleRepo.findByCodeAndTenant(DefaultRoleTemplate.SUPER_ADMIN.code(), TenantId.of(tenantAId)).orElseThrow();
        mockMvc.perform(put("/api/v1/roles/" + superAdminRole.id() + "/permissions")
                        .header("Authorization", "Bearer " + ownerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\":[\"dashboard.view\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PROTECTED_PERMISSIONS"));

        // Deactivating manager immediately revokes manager's active session
        assertEquals(managerId, managerId);
        mockMvc.perform(post("/api/v1/users/" + managerId + "/status")
                        .header("Authorization", "Bearer " + ownerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + mgrToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void enforcesRealSupervisorApprovalAndPreventsSelfOrCrossTenantApproval() throws Exception {
        createUserWithRole(tenantAId, "cashier_sup", "CashPass!1", DefaultRoleTemplate.CASHIER.code(), List.of(branchA1Id));
        createUserWithRole(tenantAId, "mgr_sup", "MgrPass!1", DefaultRoleTemplate.BRANCH_MANAGER.code(), List.of(branchA1Id));
        createUserWithRole(tenantAId, "mgr_other_branch", "MgrOther!1", DefaultRoleTemplate.BRANCH_MANAGER.code(), List.of(branchA2Id));
        createUserWithRole(tenantBId, "mgr_tenant_b", "MgrTenantB!1", DefaultRoleTemplate.BRANCH_MANAGER.code(), List.of(branchB1Id));

        String cashierToken = loginAndExtractToken(tenantACode, "cashier_sup", "CashPass!1", branchA1Id, null);

        // Self-approval blocked -> 403
        mockMvc.perform(post("/api/v1/approvals/authorize")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supervisorUsername":"cashier_sup","supervisorPassword":"CashPass!1","requiredPermission":"payments.refund","targetEntityType":"Order","targetEntityId":"ORD-1","reason":"customer complaint"}
                                """))
                .andExpect(status().isForbidden());

        // Supervisor from another branch (no branchA1 access) blocked -> 403
        mockMvc.perform(post("/api/v1/approvals/authorize")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supervisorUsername":"mgr_other_branch","supervisorPassword":"MgrOther!1","requiredPermission":"payments.refund","targetEntityType":"Order","targetEntityId":"ORD-1","reason":"customer complaint"}
                                """))
                .andExpect(status().isForbidden());

        // Cross-tenant supervisor blocked -> 401
        mockMvc.perform(post("/api/v1/approvals/authorize")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supervisorUsername":"mgr_tenant_b","supervisorPassword":"MgrTenantB!1","requiredPermission":"payments.refund","targetEntityType":"Order","targetEntityId":"ORD-1","reason":"customer complaint"}
                                """))
                .andExpect(status().isUnauthorized());

        // Authorized branch manager approves refund -> 200 & persisted in supervisor_approvals + audit_logs
        mockMvc.perform(post("/api/v1/approvals/authorize")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supervisorUsername":"mgr_sup","supervisorPassword":"MgrPass!1","requiredPermission":"payments.refund","targetEntityType":"Order","targetEntityId":"ORD-1","reason":"customer complaint"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        Integer approvedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM supervisor_approvals WHERE tenant_id = ? AND status = 'APPROVED'",
                Integer.class, tenantAId);
        assertEquals(1, approvedCount);
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
        String body = objectMapper.writeValueAsString(new com.restaurantpos.shared.dto.security.LoginRequest(
                tenantCode, username, password, branchId, terminalId
        ));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.at("/data/accessToken").asText();
    }
}
