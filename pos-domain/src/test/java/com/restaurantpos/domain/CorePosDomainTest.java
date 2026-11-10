package com.restaurantpos.domain;

import com.restaurantpos.domain.exception.DomainException;
import com.restaurantpos.domain.menu.Modifier;
import com.restaurantpos.domain.menu.ModifierGroup;
import com.restaurantpos.domain.menu.ModifierSelectionValidator;
import com.restaurantpos.domain.order.OrderStatePolicy;
import com.restaurantpos.domain.order.OrderStatus;
import com.restaurantpos.domain.order.PricingCalculator;
import com.restaurantpos.domain.table.DiningTable;
import com.restaurantpos.domain.table.TableStatePolicy;
import com.restaurantpos.domain.table.TableStatus;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorePosDomainTest {

    @Test
    void calculatesExclusiveTaxWithModifiersItemAndOrderPercentageDiscount() {
        UUID vatId = UUID.randomUUID();
        UUID line1 = UUID.randomUUID();
        UUID line2 = UUID.randomUUID();

        // Line 1: 2 * (10.00 + 1.50) = 23.00 gross, 3.00 item discount => 20.00 net
        // Line 2: 1 * (10.00 + 0.00) = 10.00 gross, 0.00 item discount => 10.00 net
        // Order discount: 10% of 30.00 = 3.00 (2.00 on line1 -> 18.00, 1.00 on line2 -> 9.00)
        // Exclusive 15% tax: line1 tax = 2.70 -> 20.70; line2 tax = 1.35 -> 10.35
        // Total subtotal = 33.00, discount = 6.00, tax = 4.05, grand total = 31.05
        var result = PricingCalculator.calculate(
                "USD",
                List.of(
                        new PricingCalculator.LineInput(
                                line1,
                                new BigDecimal("10.00"),
                                new BigDecimal("1.50"),
                                new BigDecimal("2.000"),
                                vatId,
                                "VAT 15%",
                                new BigDecimal("15.00"),
                                false,
                                new BigDecimal("3.00")
                        ),
                        new PricingCalculator.LineInput(
                                line2,
                                new BigDecimal("10.00"),
                                BigDecimal.ZERO,
                                new BigDecimal("1.000"),
                                vatId,
                                "VAT 15%",
                                new BigDecimal("15.00"),
                                false,
                                BigDecimal.ZERO
                        )
                ),
                List.of(new PricingCalculator.OrderDiscountInput("PERCENTAGE", new BigDecimal("10.00")))
        );

        assertEquals(0, new BigDecimal("33.00").compareTo(result.subtotal().amount()));
        assertEquals(0, new BigDecimal("6.00").compareTo(result.discountTotal().amount()));
        assertEquals(0, new BigDecimal("4.05").compareTo(result.taxTotal().amount()));
        assertEquals(0, new BigDecimal("31.05").compareTo(result.grandTotal().amount()));
        assertEquals(1, result.taxSummaries().size());
        assertEquals(0, new BigDecimal("27.00").compareTo(result.taxSummaries().getFirst().taxableAmount()));
        assertEquals(0, new BigDecimal("4.05").compareTo(result.taxSummaries().getFirst().taxAmount()));
    }

    @Test
    void calculatesInclusiveTaxWithoutDoubleCharging() {
        UUID vatId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();

        // 11.50 tax-inclusive at 15%: tax = 11.50 * 15 / 115 = 1.50, net = 10.00, grandTotal = 11.50
        var result = PricingCalculator.calculate(
                "USD",
                List.of(new PricingCalculator.LineInput(
                        lineId,
                        new BigDecimal("11.50"),
                        BigDecimal.ZERO,
                        BigDecimal.ONE,
                        vatId,
                        "VAT 15% Incl",
                        new BigDecimal("15.00"),
                        true,
                        BigDecimal.ZERO
                )),
                List.of()
        );

        assertEquals(0, new BigDecimal("11.50").compareTo(result.subtotal().amount()));
        assertEquals(0, new BigDecimal("1.50").compareTo(result.taxTotal().amount()));
        assertEquals(0, new BigDecimal("11.50").compareTo(result.grandTotal().amount()));
        assertEquals(0, new BigDecimal("10.00").compareTo(result.taxSummaries().getFirst().taxableAmount()));
        assertTrue(result.taxSummaries().getFirst().inclusive());
    }

    @Test
    void capsExcessiveDiscountsAtZeroWithoutNegativeGrandTotal() {
        var result = PricingCalculator.calculate(
                "USD",
                List.of(new PricingCalculator.LineInput(
                        UUID.randomUUID(),
                        new BigDecimal("5.00"),
                        BigDecimal.ZERO,
                        BigDecimal.ONE,
                        null,
                        null,
                        BigDecimal.ZERO,
                        false,
                        new BigDecimal("99.00")
                )),
                List.of(new PricingCalculator.OrderDiscountInput("FIXED_AMOUNT", new BigDecimal("50.00")))
        );

        assertEquals(0, new BigDecimal("5.00").compareTo(result.subtotal().amount()));
        assertEquals(0, new BigDecimal("5.00").compareTo(result.discountTotal().amount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.grandTotal().amount()));
    }

    @Test
    void validatesRequiredMinMaxAndActiveModifiers() {
        TenantId tenantId = TenantId.of(UUID.randomUUID());
        UUID groupId = UUID.randomUUID();
        Modifier activeMod1 = new Modifier(UUID.randomUUID(), tenantId, groupId, "MEDIUM", "Medium Rare",
                BigDecimal.ZERO, 1, true, true, 0L);
        Modifier activeMod2 = new Modifier(UUID.randomUUID(), tenantId, groupId, "WELL", "Well Done",
                new BigDecimal("0.50"), 2, false, true, 0L);
        Modifier inactiveMod = new Modifier(UUID.randomUUID(), tenantId, groupId, "RAW", "Blue",
                BigDecimal.ZERO, 3, false, false, 0L);

        ModifierGroup requiredSingle = new ModifierGroup(
                groupId, tenantId, "TEMP", "Cooking Temp", 1, 1, true, true,
                List.of(activeMod1, activeMod2, inactiveMod), 0L
        );

        // Missing required selection fails
        DomainException missing = assertThrows(DomainException.class,
                () -> ModifierSelectionValidator.validateAndResolve(List.of(requiredSingle), List.of()));
        assertEquals("MODIFIER_MIN_NOT_MET", missing.errorCode());

        // Exceeding max selection fails
        DomainException tooMany = assertThrows(DomainException.class,
                () -> ModifierSelectionValidator.validateAndResolve(
                        List.of(requiredSingle),
                        List.of(
                                new ModifierSelectionValidator.Selection(activeMod1.id(), 1),
                                new ModifierSelectionValidator.Selection(activeMod2.id(), 1)
                        )));
        assertEquals("MODIFIER_MAX_EXCEEDED", tooMany.errorCode());

        // Inactive modifier rejected
        DomainException inactive = assertThrows(DomainException.class,
                () -> ModifierSelectionValidator.validateAndResolve(
                        List.of(requiredSingle),
                        List.of(new ModifierSelectionValidator.Selection(inactiveMod.id(), 1))));
        assertEquals("INVALID_MODIFIER_SELECTION", inactive.errorCode());

        // Valid selection resolves
        var resolved = ModifierSelectionValidator.validateAndResolve(
                List.of(requiredSingle),
                List.of(new ModifierSelectionValidator.Selection(activeMod2.id(), 1))
        );
        assertEquals(1, resolved.size());
        assertEquals("Well Done", resolved.getFirst().modifier().name());
    }

    @Test
    void enforcesOrderAndTableStatePolicies() {
        OrderStatePolicy.requireTransition(OrderStatus.OPEN, OrderStatus.ON_HOLD);
        OrderStatePolicy.requireTransition(OrderStatus.ON_HOLD, OrderStatus.IN_KITCHEN);
        assertThrows(DomainException.class, () -> OrderStatePolicy.requireTransition(OrderStatus.PAID, OrderStatus.OPEN));
        assertThrows(DomainException.class, () -> OrderStatePolicy.requireMutableForItems(OrderStatus.CANCELLED));

        TenantId tenantId = TenantId.of(UUID.randomUUID());
        BranchId branchId = BranchId.of(UUID.randomUUID());
        UUID areaId = UUID.randomUUID();
        DiningTable available = new DiningTable(UUID.randomUUID(), tenantId, branchId, areaId, "Main", "T1", "Table 1",
                4, 1, TableStatus.AVAILABLE, null, true, 0L);
        TableStatePolicy.requireAvailableForOrder(available);

        DiningTable occupied = new DiningTable(available.id(), tenantId, branchId, areaId, "Main", "T1", "Table 1",
                4, 1, TableStatus.OCCUPIED, UUID.randomUUID(), true, 1L);
        assertThrows(DomainException.class, () -> TableStatePolicy.requireAvailableForOrder(occupied));
        assertThrows(DomainException.class, () -> TableStatePolicy.validateManualTransition(occupied, TableStatus.AVAILABLE));
        assertThrows(DomainException.class, () -> TableStatePolicy.validateManualTransition(available, TableStatus.OCCUPIED));
    }
}
