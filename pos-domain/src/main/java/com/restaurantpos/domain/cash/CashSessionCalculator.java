package com.restaurantpos.domain.cash;

import com.restaurantpos.shared.model.Money;

import java.math.BigDecimal;
import java.util.List;

/**
 * Derives authoritative expected cash balance strictly from append-only cash movement records.
 */
public final class CashSessionCalculator {

    private CashSessionCalculator() {
    }

    public static Money deriveExpectedBalance(String currencyCode, List<CashMovement> movements) {
        Money balance = Money.zero(currencyCode);
        if (movements != null) {
            for (CashMovement m : movements) {
                if (m.movementType().sign() > 0) {
                    balance = balance.add(Money.of(m.amount(), currencyCode));
                } else if (m.movementType().sign() < 0) {
                    balance = balance.subtract(Money.of(m.amount(), currencyCode));
                }
            }
        }
        return balance;
    }

    public static Money computeVariance(String currencyCode, BigDecimal countedCash, BigDecimal expectedCash) {
        Money counted = Money.of(countedCash, currencyCode);
        Money expected = Money.of(expectedCash, currencyCode);
        return counted.subtract(expected);
    }
}
