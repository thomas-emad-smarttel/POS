package com.restaurantpos.domain;

import com.restaurantpos.domain.exception.DomainException;
import com.restaurantpos.domain.kitchen.KitchenPriorityLevel;
import com.restaurantpos.domain.kitchen.KitchenRoutingEngine;
import com.restaurantpos.domain.kitchen.KitchenRoutingRule;
import com.restaurantpos.domain.kitchen.KitchenStation;
import com.restaurantpos.domain.kitchen.KitchenTicket;
import com.restaurantpos.domain.kitchen.KitchenTicketItem;
import com.restaurantpos.domain.kitchen.KitchenTicketItemStatus;
import com.restaurantpos.domain.kitchen.KitchenTicketStatePolicy;
import com.restaurantpos.domain.kitchen.KitchenTicketStatus;
import com.restaurantpos.shared.model.BranchId;
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

class KitchenDomainTest {

    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final BranchId BRANCH = BranchId.of(UUID.randomUUID());

    private static KitchenStation station(String code, int prepMinutes, boolean defaultStation, boolean active) {
        return new KitchenStation(
                UUID.randomUUID(), TENANT, BRANCH, code, code + " Station",
                code, null, 1, prepMinutes, true, 42, "BILINGUAL", "UTF-8",
                defaultStation, active, 0L
        );
    }

    private static KitchenTicketItem item(KitchenTicketItemStatus status) {
        return new KitchenTicketItem(
                UUID.randomUUID(), TENANT, BRANCH, UUID.randomUUID(), UUID.randomUUID(),
                "Burger", null, BigDecimal.ONE, List.of(), null, null, 10, status,
                null, null, null, null, 0L
        );
    }

    private static KitchenTicket ticket(UUID stationId, KitchenTicketStatus status) {
        return new KitchenTicket(
                UUID.randomUUID(), TENANT, BRANCH, UUID.randomUUID(), "ORD-1", "DINE_IN",
                "T1", UUID.randomUUID(), "Alice", stationId, UUID.randomUUID(),
                "KT-001", "INITIAL", status, KitchenPriorityLevel.NORMAL, 0,
                10, Instant.parse("2026-11-10T10:00:00Z"),
                null, null, null, null, null, null, null, null, 0L, List.of()
        );
    }

    @Test
    void routingEngineEnforcesProductCategoryBranchDefaultPrecedenceAndMultiStationFanOut() {
        KitchenStation grill = station("GRILL", 12, false, true);
        KitchenStation cold = station("COLD", 6, false, true);
        KitchenStation bar = station("BAR", 4, false, true);
        KitchenStation general = station("GEN", 10, false, true);

        UUID catBurgers = UUID.randomUUID();
        UUID catDrinks = UUID.randomUUID();
        UUID burgerComboProduct = UUID.randomUUID();
        UUID steakProduct = UUID.randomUUID();
        UUID juiceProduct = UUID.randomUUID();
        UUID mysteryProduct = UUID.randomUUID();

        List<KitchenRoutingRule> rules = List.of(
                // Product fan-out: burgerCombo goes to GRILL (override 15m) + COLD
                new KitchenRoutingRule(UUID.randomUUID(), TENANT, BRANCH, grill.id(), burgerComboProduct, null, 1, 15, true, 0L),
                new KitchenRoutingRule(UUID.randomUUID(), TENANT, BRANCH, cold.id(), burgerComboProduct, null, 2, null, true, 0L),
                // Category rules
                new KitchenRoutingRule(UUID.randomUUID(), TENANT, BRANCH, grill.id(), null, catBurgers, 1, null, true, 0L),
                new KitchenRoutingRule(UUID.randomUUID(), TENANT, BRANCH, bar.id(), null, catDrinks, 1, null, true, 0L),
                // Branch default rule
                new KitchenRoutingRule(UUID.randomUUID(), TENANT, BRANCH, general.id(), null, null, 99, null, true, 0L)
        );

        // 1. PRODUCT wins over CATEGORY and fans out to multiple stations
        var comboTargets = KitchenRoutingEngine.resolveStationsForItem(
                List.of(grill, cold, bar, general), rules, 10, burgerComboProduct, catBurgers);
        assertEquals(2, comboTargets.size());
        assertEquals(grill.id(), comboTargets.get(0).station().id());
        assertEquals(15, comboTargets.get(0).expectedPrepMinutes());
        assertEquals(cold.id(), comboTargets.get(1).station().id());
        assertEquals(6, comboTargets.get(1).expectedPrepMinutes());

        // 2. CATEGORY wins over BRANCH_DEFAULT
        var steakTargets = KitchenRoutingEngine.resolveStationsForItem(
                List.of(grill, cold, bar, general), rules, 10, steakProduct, catBurgers);
        assertEquals(1, steakTargets.size());
        assertEquals(grill.id(), steakTargets.get(0).station().id());

        var drinkTargets = KitchenRoutingEngine.resolveStationsForItem(
                List.of(grill, cold, bar, general), rules, 10, juiceProduct, catDrinks);
        assertEquals(1, drinkTargets.size());
        assertEquals(bar.id(), drinkTargets.get(0).station().id());

        // 3. BRANCH_DEFAULT used when no product or category rule matches
        var mysteryTargets = KitchenRoutingEngine.resolveStationsForItem(
                List.of(grill, cold, bar, general), rules, 10, mysteryProduct, UUID.randomUUID());
        assertEquals(1, mysteryTargets.size());
        assertEquals(general.id(), mysteryTargets.get(0).station().id());

        // 4. Empty target list returned when no rules or default stations apply -> unrouted item capture
        var unrouted = KitchenRoutingEngine.resolveStationsForItem(
                List.of(grill, bar), List.of(), 10, mysteryProduct, UUID.randomUUID());
        assertTrue(unrouted.isEmpty());
    }

    @Test
    void routingEngineIgnoresInactiveStationsAndFallsBackWhenDefaultStationFlagSet() {
        KitchenStation inactiveGrill = station("GRILL", 10, false, false);
        KitchenStation activeDefault = station("PASS", 8, true, true);
        UUID p = UUID.randomUUID();
        List<KitchenRoutingRule> rules = List.of(
                new KitchenRoutingRule(UUID.randomUUID(), TENANT, BRANCH, inactiveGrill.id(), p, null, 1, null, true, 0L)
        );
        var targets = KitchenRoutingEngine.resolveStationsForItem(
                List.of(inactiveGrill, activeDefault), rules, 10, p, null);
        assertEquals(1, targets.size());
        assertEquals(activeDefault.id(), targets.get(0).station().id());
    }

    @Test
    void statePolicyEnforcesTransitionsPartialReadinessOverdueAndExpoAggregation() {
        KitchenTicketStatePolicy.requireTicketTransition(KitchenTicketStatus.QUEUED, KitchenTicketStatus.ACCEPTED);
        KitchenTicketStatePolicy.requireTicketTransition(KitchenTicketStatus.ACCEPTED, KitchenTicketStatus.PREPARING);
        KitchenTicketStatePolicy.requireTicketTransition(KitchenTicketStatus.PREPARING, KitchenTicketStatus.READY);
        KitchenTicketStatePolicy.requireTicketTransition(KitchenTicketStatus.READY, KitchenTicketStatus.SERVED);

        assertThrows(DomainException.class, () ->
                KitchenTicketStatePolicy.requireTicketTransition(KitchenTicketStatus.SERVED, KitchenTicketStatus.PREPARING));
        assertThrows(DomainException.class, () ->
                KitchenTicketStatePolicy.requireTicketTransition(KitchenTicketStatus.CANCELLED, KitchenTicketStatus.ACCEPTED));

        assertThrows(DomainException.class, () ->
                KitchenTicketStatePolicy.requireItemTransition(KitchenTicketItemStatus.SERVED, KitchenTicketItemStatus.PREPARING));

        // Partial readiness: 1 READY + 1 PREPARING -> ticket NOT ready
        assertFalse(KitchenTicketStatePolicy.areAllActiveItemsReady(List.of(
                item(KitchenTicketItemStatus.READY),
                item(KitchenTicketItemStatus.PREPARING)
        )));
        // 1 READY + 1 CANCELLED -> active items satisfied
        assertTrue(KitchenTicketStatePolicy.areAllActiveItemsReady(List.of(
                item(KitchenTicketItemStatus.READY),
                item(KitchenTicketItemStatus.CANCELLED)
        )));

        // Overdue detection
        Instant firedAt = Instant.parse("2026-11-10T10:00:00Z");
        assertFalse(KitchenTicketStatePolicy.isOverdue(
                KitchenTicketStatus.PREPARING, firedAt, 10, Instant.parse("2026-11-10T10:08:00Z")));
        assertTrue(KitchenTicketStatePolicy.isOverdue(
                KitchenTicketStatus.PREPARING, firedAt, 10, Instant.parse("2026-11-10T10:11:00Z")));
        // READY / ON_HOLD / SERVED are not overdue
        assertFalse(KitchenTicketStatePolicy.isOverdue(
                KitchenTicketStatus.READY, firedAt, 10, Instant.parse("2026-11-10T10:30:00Z")));
        assertFalse(KitchenTicketStatePolicy.isOverdue(
                KitchenTicketStatus.ON_HOLD, firedAt, 10, Instant.parse("2026-11-10T10:30:00Z")));

        // Expo multi-station readiness aggregation
        UUID hot = UUID.randomUUID();
        UUID bar = UUID.randomUUID();
        assertFalse(KitchenTicketStatePolicy.isExpoOrderReady(List.of(
                ticket(hot, KitchenTicketStatus.READY),
                ticket(bar, KitchenTicketStatus.PREPARING)
        )));
        assertTrue(KitchenTicketStatePolicy.isExpoOrderReady(List.of(
                ticket(hot, KitchenTicketStatus.READY),
                ticket(bar, KitchenTicketStatus.SERVED)
        )));
        assertTrue(KitchenTicketStatePolicy.isExpoOrderReady(List.of(
                ticket(hot, KitchenTicketStatus.READY),
                ticket(bar, KitchenTicketStatus.CANCELLED)
        )));
    }
}
