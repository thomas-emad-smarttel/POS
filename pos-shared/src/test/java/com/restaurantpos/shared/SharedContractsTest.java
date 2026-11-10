package com.restaurantpos.shared;

import com.restaurantpos.shared.model.Money;
import com.restaurantpos.shared.security.PermissionCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedContractsTest {

    @Test
    void moneyAddsAndMultipliesWithExactScale() {
        Money price = Money.of(new BigDecimal("12.50"), "USD");
        Money tax = Money.of(new BigDecimal("1.25"), "USD");
        Money total = price.multiply(2).add(tax);

        assertEquals(new BigDecimal("26.25"), total.amount());
        assertEquals("USD", total.currency().getCurrencyCode());
    }

    @Test
    void moneyRejectsCurrencyMismatch() {
        Money usd = Money.of(BigDecimal.TEN, "USD");
        Money eur = Money.of(BigDecimal.TEN, "EUR");

        assertThrows(IllegalArgumentException.class, () -> usd.add(eur));
    }

    @Test
    void permissionCatalogResolvesDotDelimitedCodes() {
        assertTrue(PermissionCode.fromCode("orders.discount.approve").isPresent());
        assertEquals(PermissionCode.PAYMENTS_REFUND, PermissionCode.fromCode("payments.refund").orElseThrow());
        assertTrue(PermissionCode.fromCode("audit.view").orElseThrow().sensitive());
        assertTrue(PermissionCode.fromCode("dashboard.view").isPresent());
    }
}
