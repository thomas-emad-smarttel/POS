package com.restaurantpos.domain.payment;

import com.restaurantpos.domain.exception.DomainException;
import com.restaurantpos.domain.order.OrderStatus;
import com.restaurantpos.shared.model.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Pure domain financial calculations and safeguards for balance due, cash change,
 * overpayment prevention, and refund caps using exact {@link Money} arithmetic.
 */
public final class PaymentCalculationPolicy {

    public record TenderCalculation(
            Money appliedAmount,
            Money tenderedAmount,
            Money changeAmount,
            Money remainingDueAfter
    ) {
    }

    private PaymentCalculationPolicy() {
    }

    public static void requireOrderPayable(OrderStatus status) {
        if (status == OrderStatus.PAID || status == OrderStatus.REFUNDED || status == OrderStatus.CANCELLED || status == OrderStatus.DRAFT) {
            throw new DomainException("ORDER_NOT_PAYABLE", "Order in status " + status + " cannot accept new payments");
        }
    }

    public static Money computeRemainingDue(String currencyCode, BigDecimal orderTotal, BigDecimal netCompletedPaid) {
        Money total = Money.of(orderTotal != null ? orderTotal : BigDecimal.ZERO, currencyCode);
        Money paid = Money.of(netCompletedPaid != null ? netCompletedPaid : BigDecimal.ZERO, currencyCode);
        Money diff = total.subtract(paid);
        return diff.isNegative() ? Money.zero(currencyCode) : diff;
    }

    public static TenderCalculation validateAndComputeTender(
            String currencyCode,
            BigDecimal remainingDueRaw,
            BigDecimal requestedAmountRaw,
            BigDecimal tenderedAmountRaw,
            boolean isCashMethod
    ) {
        Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        Money remainingDue = Money.of(remainingDueRaw, currencyCode);
        if (remainingDue.isZero() || remainingDue.isNegative()) {
            throw new DomainException("ORDER_ALREADY_PAID", "Order has no remaining balance due");
        }

        Money requested = Money.of(requestedAmountRaw.setScale(Money.DEFAULT_SCALE, RoundingMode.HALF_UP), currencyCode);
        if (requested.isZero() || requested.isNegative()) {
            throw new DomainException("INVALID_PAYMENT_AMOUNT", "Payment amount must be positive");
        }

        if (requested.amount().compareTo(remainingDue.amount()) > 0) {
            if (!isCashMethod) {
                throw new DomainException(
                        "OVERPAYMENT_NOT_ALLOWED",
                        "Non-cash payment amount " + requested.amount() + " exceeds remaining due " + remainingDue.amount()
                );
            }
            // If a caller submitted total tendered cash in requestedAmount, require explicit tenderedAmount semantics
            // OR disallow requestedAmount > remainingDue unless tenderedAmount captures the excess as change.
            throw new DomainException(
                    "OVERPAYMENT_NOT_ALLOWED",
                    "Requested payment amount " + requested.amount() + " exceeds remaining due " + remainingDue.amount()
                            + "; submit cash tendered in tenderedAmount for automatic change"
            );
        }

        Money tendered = tenderedAmountRaw != null
                ? Money.of(tenderedAmountRaw.setScale(Money.DEFAULT_SCALE, RoundingMode.HALF_UP), currencyCode)
                : requested;

        if (tendered.amount().compareTo(requested.amount()) < 0) {
            throw new DomainException(
                    "INSUFFICIENT_TENDERED_AMOUNT",
                    "Tendered amount " + tendered.amount() + " cannot be less than applied amount " + requested.amount()
            );
        }

        Money change;
        if (tendered.amount().compareTo(requested.amount()) > 0) {
            if (!isCashMethod) {
                throw new DomainException("OVERPAYMENT_NOT_ALLOWED", "Change is only supported for CASH payments");
            }
            change = tendered.subtract(requested);
        } else {
            change = Money.zero(currencyCode);
        }

        Money remainingAfter = remainingDue.subtract(requested);
        return new TenderCalculation(requested, tendered, change, remainingAfter);
    }

    public static void validateRefundCap(
            String currencyCode,
            BigDecimal originalPaymentAmount,
            BigDecimal alreadyRefundedAmount,
            BigDecimal requestedRefundAmount
    ) {
        Money original = Money.of(originalPaymentAmount, currencyCode);
        Money already = Money.of(alreadyRefundedAmount != null ? alreadyRefundedAmount : BigDecimal.ZERO, currencyCode);
        Money requested = Money.of(requestedRefundAmount, currencyCode);
        if (requested.isZero() || requested.isNegative()) {
            throw new DomainException("INVALID_REFUND_AMOUNT", "Refund amount must be positive");
        }
        Money available = original.subtract(already);
        if (requested.amount().compareTo(available.amount()) > 0) {
            throw new DomainException(
                    "REFUND_LIMIT_EXCEEDED",
                    "Requested refund " + requested.amount() + " exceeds refundable balance " + available.amount()
            );
        }
    }
}
