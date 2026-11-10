package com.restaurantpos.domain.payment;

import com.restaurantpos.domain.exception.DomainException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Enforces strictly defined financial state transitions for payment attempts.
 */
public final class PaymentStatePolicy {

    private static final Map<PaymentAttemptStatus, Set<PaymentAttemptStatus>> ALLOWED = Map.of(
            PaymentAttemptStatus.PENDING, EnumSet.of(
                    PaymentAttemptStatus.PROCESSING,
                    PaymentAttemptStatus.COMPLETED,
                    PaymentAttemptStatus.FAILED,
                    PaymentAttemptStatus.CANCELLED,
                    PaymentAttemptStatus.UNCERTAIN
            ),
            PaymentAttemptStatus.PROCESSING, EnumSet.of(
                    PaymentAttemptStatus.COMPLETED,
                    PaymentAttemptStatus.FAILED,
                    PaymentAttemptStatus.CANCELLED,
                    PaymentAttemptStatus.UNCERTAIN
            ),
            PaymentAttemptStatus.UNCERTAIN, EnumSet.of(
                    PaymentAttemptStatus.COMPLETED,
                    PaymentAttemptStatus.FAILED,
                    PaymentAttemptStatus.CANCELLED
            ),
            PaymentAttemptStatus.COMPLETED, EnumSet.of(
                    PaymentAttemptStatus.PARTIALLY_REFUNDED,
                    PaymentAttemptStatus.REFUNDED
            ),
            PaymentAttemptStatus.PARTIALLY_REFUNDED, EnumSet.of(
                    PaymentAttemptStatus.PARTIALLY_REFUNDED,
                    PaymentAttemptStatus.REFUNDED
            ),
            PaymentAttemptStatus.FAILED, EnumSet.noneOf(PaymentAttemptStatus.class),
            PaymentAttemptStatus.CANCELLED, EnumSet.noneOf(PaymentAttemptStatus.class),
            PaymentAttemptStatus.REFUNDED, EnumSet.noneOf(PaymentAttemptStatus.class)
    );

    private PaymentStatePolicy() {
    }

    public static boolean isTransitionAllowed(PaymentAttemptStatus from, PaymentAttemptStatus to) {
        if (from == to) {
            return true;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireTransition(PaymentAttemptStatus from, PaymentAttemptStatus to) {
        if (!isTransitionAllowed(from, to)) {
            throw new DomainException(
                    "INVALID_PAYMENT_TRANSITION",
                    "Invalid payment state transition from " + from + " to " + to
            );
        }
    }
}
