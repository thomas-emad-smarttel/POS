package com.restaurantpos.api.config;

import com.restaurantpos.application.audit.AuditRecordingService;
import com.restaurantpos.application.kitchen.KitchenConfigService;
import com.restaurantpos.application.kitchen.KitchenOutboxConsumerService;
import com.restaurantpos.application.kitchen.KitchenPrintDispatcher;
import com.restaurantpos.application.kitchen.KitchenWorkflowService;
import com.restaurantpos.application.payment.CashSessionService;
import com.restaurantpos.application.payment.FinancialReconciliationService;
import com.restaurantpos.application.payment.PaymentPolicyConfig;
import com.restaurantpos.application.payment.PaymentService;
import com.restaurantpos.application.payment.ReceiptService;
import com.restaurantpos.application.payment.RefundService;
import com.restaurantpos.application.pos.MenuManagementService;
import com.restaurantpos.application.pos.OrderManagementService;
import com.restaurantpos.application.pos.TableManagementService;
import com.restaurantpos.application.security.AuthenticationService;
import com.restaurantpos.application.security.EffectivePermissionResolver;
import com.restaurantpos.application.security.RbacManagementService;
import com.restaurantpos.application.security.RbacSeedService;
import com.restaurantpos.application.security.SecurityPolicyConfig;
import com.restaurantpos.application.security.SupervisorApprovalService;
import com.restaurantpos.application.system.ServerHealthUseCase;
import com.restaurantpos.domain.port.AuditLogPort;
import com.restaurantpos.domain.port.AuditLogQueryPort;
import com.restaurantpos.domain.port.BranchEventPublishPort;
import com.restaurantpos.domain.port.BranchQueryPort;
import com.restaurantpos.domain.port.BranchRepositoryPort;
import com.restaurantpos.domain.port.CashDrawerPort;
import com.restaurantpos.domain.port.CashSessionRepositoryPort;
import com.restaurantpos.domain.port.KitchenDispatchPort;
import com.restaurantpos.domain.port.KitchenPayloadCodecPort;
import com.restaurantpos.domain.port.KitchenPrinterPort;
import com.restaurantpos.domain.port.KitchenRepositoryPort;
import com.restaurantpos.domain.port.MenuRepositoryPort;
import com.restaurantpos.domain.port.PasswordHasherPort;
import com.restaurantpos.domain.port.PaymentAuthorizationPort;
import com.restaurantpos.domain.port.PaymentRepositoryPort;
import com.restaurantpos.domain.port.PaymentReversalPort;
import com.restaurantpos.domain.port.PaymentStatusQueryPort;
import com.restaurantpos.domain.port.PaymentTerminalPort;
import com.restaurantpos.domain.port.PermissionCatalogPort;
import com.restaurantpos.domain.port.PosOrderRepositoryPort;
import com.restaurantpos.domain.port.ReceiptPrinterPort;
import com.restaurantpos.domain.port.ReceiptRepositoryPort;
import com.restaurantpos.domain.port.RoleRepositoryPort;
import com.restaurantpos.domain.port.SupervisorApprovalRepositoryPort;
import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.domain.port.TableRepositoryPort;
import com.restaurantpos.domain.port.TenantQueryPort;
import com.restaurantpos.domain.port.TenantRepositoryPort;
import com.restaurantpos.domain.port.UserRepositoryPort;
import com.restaurantpos.domain.port.UserSessionRepositoryPort;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for pure application use cases exposed by the API module.
 */
@Configuration
public class ApiWiringConfig {

    @Bean
    public ServerHealthUseCase serverHealthUseCase(SystemClockPort systemClockPort) {
        return new ServerHealthUseCase(systemClockPort);
    }

    @Bean
    public AuditRecordingService auditRecordingService(AuditLogPort auditLogPort, SystemClockPort systemClockPort) {
        return new AuditRecordingService(auditLogPort, systemClockPort);
    }

    @Bean
    public SecurityPolicyConfig securityPolicyConfig(
            @Value("${pos.security.session-ttl-minutes:480}") long sessionTtlMinutes,
            @Value("${pos.security.max-active-sessions-per-user:5}") int maxActiveSessionsPerUser,
            @Value("${pos.security.max-failed-login-attempts:5}") int maxFailedLoginAttempts,
            @Value("${pos.security.lockout-duration-minutes:15}") long lockoutDurationMinutes,
            @Value("${pos.security.throttle-window-seconds:60}") long throttleWindowSeconds,
            @Value("${pos.security.max-throttle-attempts-per-key:10}") int maxThrottleAttemptsPerKey,
            @Value("${pos.security.approval-ttl-minutes:5}") long approvalTtlMinutes
    ) {
        return new SecurityPolicyConfig(
                sessionTtlMinutes,
                maxActiveSessionsPerUser,
                maxFailedLoginAttempts,
                lockoutDurationMinutes,
                throttleWindowSeconds,
                maxThrottleAttemptsPerKey,
                approvalTtlMinutes
        );
    }

    @Bean
    @ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
    public EffectivePermissionResolver effectivePermissionResolver(
            UserRepositoryPort userRepositoryPort,
            RoleRepositoryPort roleRepositoryPort
    ) {
        return new EffectivePermissionResolver(userRepositoryPort, roleRepositoryPort);
    }

    @Bean
    @ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
    public RbacSeedService rbacSeedService(
            PermissionCatalogPort permissionCatalogPort,
            RoleRepositoryPort roleRepositoryPort
    ) {
        return new RbacSeedService(permissionCatalogPort, roleRepositoryPort);
    }

    @Bean
    @ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
    public AuthenticationService authenticationService(
            TenantQueryPort tenantQueryPort,
            UserRepositoryPort userRepositoryPort,
            UserSessionRepositoryPort userSessionRepositoryPort,
            PasswordHasherPort passwordHasherPort,
            EffectivePermissionResolver effectivePermissionResolver,
            AuditRecordingService auditRecordingService,
            SystemClockPort systemClockPort,
            SecurityPolicyConfig securityPolicyConfig
    ) {
        return new AuthenticationService(
                tenantQueryPort,
                userRepositoryPort,
                userSessionRepositoryPort,
                passwordHasherPort,
                effectivePermissionResolver,
                auditRecordingService,
                systemClockPort,
                securityPolicyConfig
        );
    }

    @Bean
    @ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
    public RbacManagementService rbacManagementService(
            UserRepositoryPort userRepositoryPort,
            RoleRepositoryPort roleRepositoryPort,
            PermissionCatalogPort permissionCatalogPort,
            BranchRepositoryPort branchRepositoryPort,
            BranchQueryPort branchQueryPort,
            UserSessionRepositoryPort userSessionRepositoryPort,
            AuditLogQueryPort auditLogQueryPort,
            PasswordHasherPort passwordHasherPort,
            AuditRecordingService auditRecordingService,
            SystemClockPort systemClockPort
    ) {
        return new RbacManagementService(
                userRepositoryPort,
                roleRepositoryPort,
                permissionCatalogPort,
                branchRepositoryPort,
                branchQueryPort,
                userSessionRepositoryPort,
                auditLogQueryPort,
                passwordHasherPort,
                auditRecordingService,
                systemClockPort
        );
    }

    @Bean
    @ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
    public SupervisorApprovalService supervisorApprovalService(
            UserRepositoryPort userRepositoryPort,
            PasswordHasherPort passwordHasherPort,
            EffectivePermissionResolver effectivePermissionResolver,
            SupervisorApprovalRepositoryPort approvalRepositoryPort,
            AuditRecordingService auditRecordingService,
            SystemClockPort systemClockPort,
            SecurityPolicyConfig securityPolicyConfig
    ) {
        return new SupervisorApprovalService(
                userRepositoryPort,
                passwordHasherPort,
                effectivePermissionResolver,
                approvalRepositoryPort,
                auditRecordingService,
                systemClockPort,
                securityPolicyConfig
        );
    }

    @Bean
    @ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
    public MenuManagementService menuManagementService(
            MenuRepositoryPort menuRepositoryPort,
            AuditRecordingService auditRecordingService,
            BranchEventPublishPort eventPublishPort
    ) {
        return new MenuManagementService(menuRepositoryPort, auditRecordingService, eventPublishPort);
    }

    @Bean
    @ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
    public TableManagementService tableManagementService(
            TableRepositoryPort tableRepositoryPort,
            AuditRecordingService auditRecordingService,
            BranchEventPublishPort eventPublishPort
    ) {
        return new TableManagementService(tableRepositoryPort, auditRecordingService, eventPublishPort);
    }

    @Bean
    @ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
    public OrderManagementService orderManagementService(
            PosOrderRepositoryPort orderRepositoryPort,
            MenuRepositoryPort menuRepositoryPort,
            TableRepositoryPort tableRepositoryPort,
            BranchRepositoryPort branchRepositoryPort,
            UserRepositoryPort userRepositoryPort,
            RoleRepositoryPort roleRepositoryPort,
            KitchenDispatchPort kitchenDispatchPort,
            BranchEventPublishPort eventPublishPort,
            AuditRecordingService auditRecordingService,
            SystemClockPort systemClockPort
    ) {
        return new OrderManagementService(
                orderRepositoryPort,
                menuRepositoryPort,
                tableRepositoryPort,
                branchRepositoryPort,
                userRepositoryPort,
                roleRepositoryPort,
                kitchenDispatchPort,
                eventPublishPort,
                auditRecordingService,
                systemClockPort
        );
    }

    @Bean
    public PaymentPolicyConfig paymentPolicyConfig(
            @Value("${pos.payment.refund-approval-threshold:25.00}") BigDecimal refundApprovalThreshold,
            @Value("${pos.receipt.paper-width:42}") int paperWidth,
            @Value("${pos.receipt.encoding:UTF-8}") String encoding,
            @Value("${pos.receipt.language:BILINGUAL}") String language,
            @Value("${pos.receipt.auto-print:true}") boolean autoPrint
    ) {
        return new PaymentPolicyConfig(refundApprovalThreshold, paperWidth, encoding, language, autoPrint);
    }

    @Bean
    @ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
    public ReceiptService receiptService(
            ReceiptRepositoryPort receiptRepositoryPort,
            ReceiptPrinterPort receiptPrinterPort,
            PosOrderRepositoryPort orderRepositoryPort,
            PaymentRepositoryPort paymentRepositoryPort,
            TenantRepositoryPort tenantRepositoryPort,
            BranchRepositoryPort branchRepositoryPort,
            UserRepositoryPort userRepositoryPort,
            AuditRecordingService auditRecordingService,
            SystemClockPort systemClockPort,
            PaymentPolicyConfig paymentPolicyConfig
    ) {
        return new ReceiptService(
                receiptRepositoryPort, receiptPrinterPort, orderRepositoryPort, paymentRepositoryPort,
                tenantRepositoryPort, branchRepositoryPort, userRepositoryPort,
                auditRecordingService, systemClockPort, paymentPolicyConfig
        );
    }

    @Bean
    @ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
    public CashSessionService cashSessionService(
            CashSessionRepositoryPort cashSessionRepositoryPort,
            PaymentRepositoryPort paymentRepositoryPort,
            BranchRepositoryPort branchRepositoryPort,
            SupervisorApprovalRepositoryPort approvalRepositoryPort,
            CashDrawerPort cashDrawerPort,
            ReceiptService receiptService,
            AuditRecordingService auditRecordingService,
            SystemClockPort systemClockPort
    ) {
        return new CashSessionService(
                cashSessionRepositoryPort, paymentRepositoryPort, branchRepositoryPort,
                approvalRepositoryPort, cashDrawerPort, receiptService, auditRecordingService, systemClockPort
        );
    }

    @Bean
    @ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
    public PaymentService paymentService(
            PaymentRepositoryPort paymentRepositoryPort,
            PosOrderRepositoryPort orderRepositoryPort,
            TableRepositoryPort tableRepositoryPort,
            CashSessionService cashSessionService,
            ReceiptService receiptService,
            PaymentTerminalPort paymentTerminalPort,
            PaymentAuthorizationPort paymentAuthorizationPort,
            PaymentStatusQueryPort paymentStatusQueryPort,
            CashDrawerPort cashDrawerPort,
            BranchEventPublishPort eventPublishPort,
            AuditRecordingService auditRecordingService,
            SystemClockPort systemClockPort
    ) {
        return new PaymentService(
                paymentRepositoryPort, orderRepositoryPort, tableRepositoryPort,
                cashSessionService, receiptService, paymentTerminalPort, paymentAuthorizationPort,
                paymentStatusQueryPort, cashDrawerPort, eventPublishPort, auditRecordingService, systemClockPort
        );
    }

    @Bean
    @ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
    public RefundService refundService(
            PaymentRepositoryPort paymentRepositoryPort,
            PosOrderRepositoryPort orderRepositoryPort,
            SupervisorApprovalRepositoryPort approvalRepositoryPort,
            PaymentReversalPort paymentReversalPort,
            CashSessionService cashSessionService,
            PaymentService paymentService,
            ReceiptService receiptService,
            BranchEventPublishPort eventPublishPort,
            AuditRecordingService auditRecordingService,
            SystemClockPort systemClockPort,
            PaymentPolicyConfig paymentPolicyConfig
    ) {
        return new RefundService(
                paymentRepositoryPort, orderRepositoryPort, approvalRepositoryPort,
                paymentReversalPort, cashSessionService, paymentService, receiptService,
                eventPublishPort, auditRecordingService, systemClockPort, paymentPolicyConfig
        );
    }

    @Bean
    @ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
    public FinancialReconciliationService financialReconciliationService(
            PaymentRepositoryPort paymentRepositoryPort,
            CashSessionRepositoryPort cashSessionRepositoryPort,
            PaymentService paymentService
    ) {
        return new FinancialReconciliationService(paymentRepositoryPort, cashSessionRepositoryPort, paymentService);
    }

    @Bean
    @ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
    public KitchenPrintDispatcher kitchenPrintDispatcher(
            KitchenRepositoryPort kitchenRepositoryPort,
            KitchenPrinterPort kitchenPrinterPort,
            AuditRecordingService auditRecordingService,
            SystemClockPort systemClockPort
    ) {
        return new KitchenPrintDispatcher(
                kitchenRepositoryPort, kitchenPrinterPort, auditRecordingService, systemClockPort
        );
    }

    @Bean
    @ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
    public KitchenConfigService kitchenConfigService(
            KitchenRepositoryPort kitchenRepositoryPort,
            MenuRepositoryPort menuRepositoryPort,
            KitchenPrintDispatcher kitchenPrintDispatcher,
            BranchEventPublishPort eventPublishPort,
            AuditRecordingService auditRecordingService,
            SystemClockPort systemClockPort
    ) {
        return new KitchenConfigService(
                kitchenRepositoryPort, menuRepositoryPort, kitchenPrintDispatcher,
                eventPublishPort, auditRecordingService, systemClockPort
        );
    }

    @Bean
    @ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
    public KitchenOutboxConsumerService kitchenOutboxConsumerService(
            KitchenRepositoryPort kitchenRepositoryPort,
            KitchenPayloadCodecPort kitchenPayloadCodecPort,
            KitchenPrintDispatcher kitchenPrintDispatcher,
            BranchEventPublishPort eventPublishPort,
            AuditRecordingService auditRecordingService,
            SystemClockPort systemClockPort
    ) {
        return new KitchenOutboxConsumerService(
                kitchenRepositoryPort, kitchenPayloadCodecPort, kitchenPrintDispatcher,
                eventPublishPort, auditRecordingService, systemClockPort
        );
    }

    @Bean
    @ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
    public KitchenWorkflowService kitchenWorkflowService(
            KitchenRepositoryPort kitchenRepositoryPort,
            KitchenPrintDispatcher kitchenPrintDispatcher,
            BranchEventPublishPort eventPublishPort,
            AuditRecordingService auditRecordingService,
            SystemClockPort systemClockPort
    ) {
        return new KitchenWorkflowService(
                kitchenRepositoryPort, kitchenPrintDispatcher, eventPublishPort,
                auditRecordingService, systemClockPort
        );
    }
}
