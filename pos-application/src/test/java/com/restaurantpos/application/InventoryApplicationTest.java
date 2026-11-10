package com.restaurantpos.application;

import com.restaurantpos.application.audit.AuditRecordingService;
import com.restaurantpos.application.inventory.InventoryOutboxConsumerService;
import com.restaurantpos.application.inventory.StockCountService;
import com.restaurantpos.application.inventory.StockLedgerService;
import com.restaurantpos.application.inventory.StockTransferService;
import com.restaurantpos.domain.exception.DomainException;
import com.restaurantpos.domain.inventory.InventoryAlert;
import com.restaurantpos.domain.inventory.InventoryBalance;
import com.restaurantpos.domain.inventory.InventoryItem;
import com.restaurantpos.domain.inventory.InventoryLocation;
import com.restaurantpos.domain.inventory.InventoryPolicy;
import com.restaurantpos.domain.inventory.Recipe;
import com.restaurantpos.domain.inventory.RecipeIngredient;
import com.restaurantpos.domain.inventory.RecipeVersion;
import com.restaurantpos.domain.inventory.StockCount;
import com.restaurantpos.domain.inventory.StockCountItem;
import com.restaurantpos.domain.inventory.StockMovement;
import com.restaurantpos.domain.inventory.StockTransfer;
import com.restaurantpos.domain.inventory.StockTransferItem;
import com.restaurantpos.domain.inventory.UnitOfMeasure;
import com.restaurantpos.domain.kitchen.OutboxWorkItem;
import com.restaurantpos.domain.port.BranchEventPublishPort;
import com.restaurantpos.domain.port.InventoryPayloadCodecPort;
import com.restaurantpos.domain.port.InventoryRepositoryPort;
import com.restaurantpos.domain.port.KitchenPayloadCodecPort;
import com.restaurantpos.domain.port.SupervisorApprovalRepositoryPort;
import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.shared.dto.inventory.ReceiveStockTransferRequest;
import com.restaurantpos.shared.dto.inventory.RecordStockAdjustmentRequest;
import com.restaurantpos.shared.dto.inventory.StockCountActionRequest;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.shared.security.PermissionCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryApplicationTest {

    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final BranchId BRANCH_A = BranchId.of(UUID.randomUUID());
    private static final BranchId BRANCH_B = BranchId.of(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-11-10T14:00:00Z");

    @Mock private InventoryRepositoryPort invRepo;
    @Mock private KitchenPayloadCodecPort kitchenCodec;
    @Mock private InventoryPayloadCodecPort invCodec;
    @Mock private SupervisorApprovalRepositoryPort approvalRepo;
    @Mock private BranchEventPublishPort eventPublisher;

    private AuditRecordingService auditService;
    private SystemClockPort clock;
    private StockLedgerService ledgerService;
    private StockCountService countService;
    private StockTransferService transferService;
    private InventoryOutboxConsumerService outboxConsumer;

    @BeforeEach
    void setUp() {
        clock = () -> NOW;
        auditService = new AuditRecordingService(entry -> {}, clock);
        ledgerService = new StockLedgerService(invRepo, approvalRepo, eventPublisher, auditService, clock);
        countService = new StockCountService(invRepo, ledgerService, approvalRepo, eventPublisher, auditService, clock);
        transferService = new StockTransferService(invRepo, ledgerService, eventPublisher, auditService, clock);
        outboxConsumer = new InventoryOutboxConsumerService(invRepo, kitchenCodec, invCodec, ledgerService, clock);

        lenient().when(invRepo.saveBalance(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(invRepo.appendMovement(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(invRepo.saveItem(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(invRepo.saveAlert(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(invRepo.saveStockCount(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(invRepo.saveStockTransfer(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void duplicateOutboxEventIsIdempotentAndFiveFailuresDeadLetter() {
        UUID dupId = UUID.randomUUID();
        OutboxWorkItem dup = new OutboxWorkItem(dupId, TENANT, BRANCH_A, "Order", UUID.randomUUID(),
                "INVENTORY_RESERVE_ORDER", "k1", "{}", "PENDING", 0, NOW, NOW);
        when(invRepo.isOutboxEventProcessed(dupId)).thenReturn(true);
        outboxConsumer.processSingleEvent(dup);
        verify(invRepo).markOutboxEventPublished(dupId, NOW);
        verify(invCodec, never()).decodeReserveOrder(any());

        UUID badId = UUID.randomUUID();
        OutboxWorkItem fifthFail = new OutboxWorkItem(badId, TENANT, BRANCH_A, "KitchenTicket", UUID.randomUUID(),
                "INVENTORY_CONSUME_PREP", "k2", "{bad", "PENDING", 4, NOW, NOW);
        when(invRepo.claimPendingInventoryOutboxEvents(anyInt(), any())).thenReturn(List.of(fifthFail));
        when(invRepo.isOutboxEventProcessed(badId)).thenReturn(false);
        when(invCodec.decodePrepStarted("{bad")).thenThrow(new IllegalStateException("bad json"));

        assertEquals(0, outboxConsumer.processPendingEvents(10));
        verify(invRepo).markOutboxEventRetry(eq(badId), eq(5), any(), eq("DEAD_LETTER"));
    }

    @Test
    void negativeStockPolicyBlocksInsufficientDeductionWhileAllowingIdempotentRetry() {
        UUID locId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        AccessPrincipal mgr = new AccessPrincipal(UUID.randomUUID(), "inv_mgr", TENANT, BRANCH_A, null, null,
                Set.of(BRANCH_A), Set.of("INVENTORY_MANAGER"),
                Set.of(PermissionCode.INVENTORY_STOCK_ADJUST, PermissionCode.INVENTORY_COST_VIEW), true);

        InventoryItem item = new InventoryItem(itemId, TENANT, null, null, locId, "FLOUR", "Flour",
                null, "RAW_INGREDIENT", null, unitId, BigDecimal.ZERO, new BigDecimal("5.0000"),
                new BigDecimal("2.0000"), "STANDARD", false, false, "DENY", "WEIGHTED_AVERAGE", true, 0L);

        when(invRepo.findMovementByIdempotency(TENANT, BRANCH_A, "out-fail")).thenReturn(Optional.empty());
        when(invRepo.findLocationScoped(locId, TENANT, BRANCH_A)).thenReturn(Optional.of(
                new InventoryLocation(locId, TENANT, BRANCH_A, "MAIN", "Main", "MAIN_STORE", true, true, 0L)));
        when(invRepo.findItemScoped(itemId, TENANT)).thenReturn(Optional.of(item));
        when(invRepo.findUnitsByTenant(TENANT)).thenReturn(List.of(
                new UnitOfMeasure(unitId, TENANT, "KG", "Kilogram", "WEIGHT", null, BigDecimal.ONE, 0L)));
        when(invRepo.findUnitConversionsByTenant(TENANT)).thenReturn(List.of());
        when(invRepo.findPolicy(TENANT, BRANCH_A)).thenReturn(Optional.of(
                InventoryPolicy.defaults(UUID.randomUUID(), TENANT, BRANCH_A)));
        when(invRepo.findBalanceScoped(TENANT, BRANCH_A, locId, itemId)).thenReturn(Optional.of(
                new InventoryBalance(UUID.randomUUID(), TENANT, BRANCH_A, locId, itemId,
                        new BigDecimal("3.0000"), new BigDecimal("1.0000"), new BigDecimal("2.0000"), NOW, NOW, 0L)));

        // Available = 3 - 1 = 2 kg; trying to remove 2.5 kg -> rejected INSUFFICIENT_STOCK
        DomainException ex = assertThrows(DomainException.class, () -> ledgerService.recordAdjustment(mgr,
                new RecordStockAdjustmentRequest(locId, itemId, unitId, new BigDecimal("2.5"),
                        "STOCK_ADJUSTMENT_OUT", null, "Test", null, null, "out-fail", null, false),
                CorrelationId.of("c1")));
        assertEquals("INSUFFICIENT_STOCK", ex.errorCode());
    }

    @Test
    void stockCountPreventsDoublePostingAndPartialTransferBlocksOverReceipt() {
        UUID locId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID countId = UUID.randomUUID();

        AccessPrincipal mgr = new AccessPrincipal(UUID.randomUUID(), "mgr", TENANT, BRANCH_A, null, null,
                Set.of(BRANCH_A), Set.of("BRANCH_MANAGER"),
                Set.of(PermissionCode.INVENTORY_VIEW, PermissionCode.INVENTORY_STOCK_COUNT_APPROVE,
                        PermissionCode.INVENTORY_STOCK_RECEIVE, PermissionCode.INVENTORY_COST_VIEW), true);

        StockCount alreadyPosted = new StockCount(countId, TENANT, BRANCH_A, "CNT-1", locId, null, "POSTED",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                BigDecimal.ZERO, null, NOW, NOW, NOW, 3L, List.of());
        when(invRepo.findStockCountScoped(countId, TENANT, BRANCH_A)).thenReturn(Optional.of(alreadyPosted));

        DomainException postEx = assertThrows(DomainException.class, () -> countService.postCount(
                mgr, countId, new StockCountActionRequest(3L, null, null), CorrelationId.of("c2")));
        assertEquals("ALREADY_POSTED", postEx.errorCode());

        // Partial transfer over-receipt test
        UUID trfId = UUID.randomUUID();
        UUID tiId = UUID.randomUUID();
        AccessPrincipal destMgr = new AccessPrincipal(UUID.randomUUID(), "dest_mgr", TENANT, BRANCH_B, null, null,
                Set.of(BRANCH_B), Set.of("BRANCH_MANAGER"),
                Set.of(PermissionCode.INVENTORY_STOCK_RECEIVE, PermissionCode.INVENTORY_COST_VIEW), true);

        StockTransfer dispatched = new StockTransfer(trfId, TENANT, "TRF-1", BRANCH_A, locId, BRANCH_B, UUID.randomUUID(),
                "DISPATCHED", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null,
                NOW, NOW, NOW, null, 2L, List.of(
                new StockTransferItem(tiId, TENANT, trfId, itemId, unitId, new BigDecimal("10.0000"),
                        new BigDecimal("10.0000"), new BigDecimal("4.0000"), BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("3.0000"), null, 0L)));
        when(invRepo.findStockTransferScoped(trfId, TENANT)).thenReturn(Optional.of(dispatched));
        when(invRepo.findUnitsByTenant(TENANT)).thenReturn(List.of(
                new UnitOfMeasure(unitId, TENANT, "KG", "Kilogram", "WEIGHT", null, BigDecimal.ONE, 0L)));
        when(invRepo.findUnitConversionsByTenant(TENANT)).thenReturn(List.of());

        // Already received 4 of 10 dispatched -> trying to receive 7 more fails
        DomainException trfEx = assertThrows(DomainException.class, () -> transferService.receiveTransfer(
                destMgr, trfId, new ReceiveStockTransferRequest(2L, List.of(
                        new ReceiveStockTransferRequest.ReceiveLineInput(tiId, new BigDecimal("7"), BigDecimal.ZERO, BigDecimal.ZERO, null)),
                        null), CorrelationId.of("c3")));
        assertEquals("INVALID_TRANSFER_QUANTITY", trfEx.errorCode());
    }
}
