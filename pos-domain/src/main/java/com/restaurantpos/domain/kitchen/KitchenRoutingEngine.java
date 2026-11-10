package com.restaurantpos.domain.kitchen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic kitchen routing resolver:
 * Precedence: PRODUCT rule(s) -> CATEGORY rule(s) -> BRANCH_DEFAULT rule(s) or defaultStation flag.
 * Supports multi-station preparation (e.g. product routed to multiple active stations).
 * Returns empty target list when no active route applies so items become explicit unrouted exceptions.
 */
public final class KitchenRoutingEngine {

    public record RoutingTarget(KitchenStation station, int expectedPrepMinutes) {
    }

    private KitchenRoutingEngine() {
    }

    public static List<RoutingTarget> resolveStationsForItem(
            List<KitchenStation> activeStations,
            List<KitchenRoutingRule> rules,
            int branchDefaultPrepMinutes,
            UUID productId,
            UUID categoryId
    ) {
        if (activeStations == null || activeStations.isEmpty()) {
            return List.of();
        }
        Map<UUID, KitchenStation> stationById = new LinkedHashMap<>();
        for (KitchenStation s : activeStations) {
            if (s.active()) {
                stationById.put(s.id(), s);
            }
        }
        if (stationById.isEmpty()) {
            return List.of();
        }

        List<KitchenRoutingRule> sortedRules = (rules == null ? List.<KitchenRoutingRule>of() : rules).stream()
                .filter(KitchenRoutingRule::active)
                .filter(r -> stationById.containsKey(r.kitchenStationId()))
                .sorted(Comparator.comparingInt(KitchenRoutingRule::precedence))
                .toList();

        // 1. PRODUCT-level rules
        List<KitchenRoutingRule> productMatches = sortedRules.stream()
                .filter(r -> r.productId() != null && r.productId().equals(productId))
                .toList();
        if (!productMatches.isEmpty()) {
            return deduplicateTargets(productMatches, stationById, branchDefaultPrepMinutes);
        }

        // 2. CATEGORY-level rules
        if (categoryId != null) {
            List<KitchenRoutingRule> categoryMatches = sortedRules.stream()
                    .filter(r -> r.productId() == null && r.categoryId() != null && r.categoryId().equals(categoryId))
                    .toList();
            if (!categoryMatches.isEmpty()) {
                return deduplicateTargets(categoryMatches, stationById, branchDefaultPrepMinutes);
            }
        }

        // 3. BRANCH_DEFAULT rules
        List<KitchenRoutingRule> branchDefaultMatches = sortedRules.stream()
                .filter(r -> r.productId() == null && r.categoryId() == null)
                .toList();
        if (!branchDefaultMatches.isEmpty()) {
            return deduplicateTargets(branchDefaultMatches, stationById, branchDefaultPrepMinutes);
        }

        // 4. Station default flag fallback
        List<RoutingTarget> fallback = new ArrayList<>();
        for (KitchenStation st : stationById.values()) {
            if (st.defaultStation()) {
                int prep = st.expectedPrepMinutes() > 0 ? st.expectedPrepMinutes() : branchDefaultPrepMinutes;
                fallback.add(new RoutingTarget(st, prep));
            }
        }
        return List.copyOf(fallback);
    }

    private static List<RoutingTarget> deduplicateTargets(
            List<KitchenRoutingRule> matchedRules,
            Map<UUID, KitchenStation> stationById,
            int branchDefaultPrepMinutes
    ) {
        Map<UUID, RoutingTarget> byStation = new LinkedHashMap<>();
        for (KitchenRoutingRule rule : matchedRules) {
            KitchenStation st = stationById.get(rule.kitchenStationId());
            if (st != null && !byStation.containsKey(st.id())) {
                int prep = rule.expectedPrepMinutesOverride() != null && rule.expectedPrepMinutesOverride() > 0
                        ? rule.expectedPrepMinutesOverride()
                        : (st.expectedPrepMinutes() > 0 ? st.expectedPrepMinutes() : branchDefaultPrepMinutes);
                byStation.put(st.id(), new RoutingTarget(st, prep));
            }
        }
        return List.copyOf(byStation.values());
    }
}
