package com.restaurantpos.domain;

import com.restaurantpos.domain.cash.CashMovement;
import com.restaurantpos.domain.cash.CashMovementType;
import com.restaurantpos.domain.cash.CashSessionCalculator;
import com.restaurantpos.domain.exception.DomainException;
import com.restaurantpos.domain.order.OrderStatus;
import com.restaurantpos.domain.payment.PaymentAttemptStatus;
import com.restaurantpos.domain.payment.PaymentCalculationPolicy;
import com.restaurantpos.domain.payment.PaymentStatePolicy;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.Money;
import com.restaurantpos.shared.model.TenantId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentAndCashDomainTest {

    @Test
    void enforcesPartialSplitCashChangeAndOverpaymentRules() {
        PaymentCalculationPolicy.requireOrderPayable(OrderStatus.OPEN);
        assertThrows(DomainException.class, () -> PaymentCalculationPolicy.requireOrderPayable(OrderStatus.PAID));
        assertThrows(DomainException.class, () -> PaymentCalculationPolicy.requireOrderPayable(OrderStatus.CANCELLED));

        // Partial cash payment with tendered change: 40.00 due, apply 15.00, tendered 20.00 -> change 5.00, rem 25.00
        var partialCash = PaymentCalculationPolicy.validateAndComputeTender(
                "USD", new BigDecimal("40.00"), new BigDecimal("15.00"), new BigDecimal("20.00"), true);
        assertEquals(0, new BigDecimal("15.00").compareTo(partialCash.appliedAmount().amount()));
        assertEquals(0, new BigDecimal("5.00").compareTo(partialCash.changeAmount().amount()));
        assertEquals(0, new BigDecimal("25.00").compareTo(partialCash.remainingDueAfter().amount()));

        // Split final card payment: 25.00 due, apply 25.00 -> rem 0.00
        var finalCard = PaymentCalculationPolicy.validateAndComputeTender(
                "USD", partialCash.remainingDueAfter().amount(), new BigDecimal("25.00"), new BigDecimal("25.00"), false);
        assertTrue(finalCard.remainingDueAfter().isZero());

        // Overpayment rejected
        assertThrows(DomainException.class, () -> PaymentCalculationPolicy.validateAndComputeTender(
                "USD", new BigDecimal("25.00"), new BigDecimal("30.00"), new BigDecimal("30.00"), false));
        assertThrows(DomainException.class, () -> PaymentCalculationPolicy.validateAndComputeTender(
                "USD", new BigDecimal("25.00"), new BigDecimal("10.00"), new BigDecimal("20.00"), false));
    }

    @Test
    void enforcesRefundCapsAndExplicitPaymentStateMachine() {
        PaymentCalculationPolicy.validateRefundCap("USD", new BigDecimal("50.00"), new BigDecimal("20.00"), new BigDecimal("30.00"));
        assertThrows(DomainException.class, () -> PaymentCalculationPolicy.validateRefundCap(
                "USD", new BigDecimal("50.00"), new BigDecimal("20.00"), new BigDecimal("30.01")));

        PaymentStatePolicy.requireTransition(PaymentAttemptStatus.PENDING, PaymentAttemptStatus.PROCESSING);
        PaymentStatePolicy.requireTransition(PaymentAttemptStatus.PROCESSING, PaymentAttemptStatus.UNCERTAIN);
        PaymentStatePolicy.requireTransition(PaymentAttemptStatus.UNCERTAIN, PaymentAttemptStatus.COMPLETED);
        PaymentStatePolicy.requireTransition(PaymentAttemptStatus.COMPLETED, PaymentAttemptStatus.PARTIALLY_REFUNDED);
        PaymentStatePolicy.requireTransition(PaymentAttemptStatus.PARTIALLY_REFUNDED, PaymentAttemptStatus.REFUNDED);

        assertFalse(PaymentStatePolicy.isTransitionAllowed(PaymentAttemptStatus.FAILED, PaymentAttemptStatus.COMPLETED));
        assertFalse(PaymentStatePolicy.isTransitionAllowed(PaymentAttemptStatus.CANCELLED, PaymentAttemptStatus.COMPLETED));
        assertThrows(DomainException.class, () ->
                PaymentStatePolicy.requireTransition(PaymentAttemptStatus.REFUNDED, PaymentAttemptStatus.PROCESSING));
    }

    @Test
    void derivesExpectedCashBalanceAndVarianceFromAppendOnlyMovements() {
        TenantId t = TenantId.of(UUID.randomUUID());
        BranchId b = BranchId.of(UUID.randomUUID());
        UUID s = UUID.randomUUID();
        UUID u = UUID.randomUUID();
        Instant now = Instant.now();

        List<CashMovement> movements = List.of(
                new CashMovement(UUID.randomUUID(), t, b, s, u, CashMovementType.OPENING_FLOAT, new BigDecimal("100.00"), "Float", "c1", null, now),
                new CashMovement(UUID.randomUUID(), t, b, s, u, CashMovementType.CASH_SALE, new BigDecimal("45.50"), "Sale", "c2", null, now),
                new CashMovement(UUID.randomUUID(), t, b, s, u, CashMovementType.PAY_IN, new BigDecimal("20.00"), "Bank change", "c3", null, now),
                new CashMovement(UUID.randomUUID(), t, b, s, u, CashMovementType.CASH_REFUND, new BigDecimal("10.00"), "Refund", "c4", null, now),
                new CashMovement(UUID.randomUUID(), t, b, s, u, CashMovementType.PAY_OUT, new BigDecimal("15.50"), "Supplies", "c5", null, now),
                new CashMovement(UUID.randomUUID(), t, b, s, u, CashMovementType.DRAWER_NO_SALE, BigDecimal.ZERO, "No sale", "c6", null, now)
        );

        Money expected = CashSessionCalculator.deriveExpectedBalance("USD", movements);
        assertEquals(0, new BigDecimal("140.00").compareTo(expected.amount()));

        Money variance = CashSessionCalculator.computeVariance("USD", new BigDecimal("138.25"), expected.amount());
        assertEquals(0, new BigDecimal("-1.75").compareTo(variance.amount()));
    }
}
