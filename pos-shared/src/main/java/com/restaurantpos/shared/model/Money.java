package com.restaurantpos.shared.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable monetary value object enforcing explicit scale and currency safety.
 */
public record Money(BigDecimal amount, Currency currency) {

    public static final int DEFAULT_SCALE = 2;

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        amount = amount.setScale(DEFAULT_SCALE, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    public static Money zero(String currencyCode) {
        return of(BigDecimal.ZERO, currencyCode);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money multiply(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must not be negative: " + quantity);
        }
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
    }

    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other money must not be null");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + currency.getCurrencyCode() + " vs " + other.currency.getCurrencyCode()
            );
        }
    }
}
