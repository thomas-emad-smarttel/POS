package com.restaurantpos.domain.inventory;

import com.restaurantpos.domain.exception.DomainException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pure BigDecimal unit conversion service supporting weight, volume, count, and configurable item/package factors.
 * Preserves intermediate precision at scale 8 and outputs clean ingredient/ledger scales without floating-point rounding loss.
 */
public final class UnitConversionService {

    public static final int QUANTITY_SCALE = 4;
    public static final int HIGH_PRECISION_SCALE = 6;
    private static final MathContext MC = new MathContext(18, RoundingMode.HALF_UP);

    private UnitConversionService() {
    }

    public static BigDecimal convert(
            BigDecimal quantity,
            UUID fromUnitId,
            UUID toUnitId,
            UUID inventoryItemId,
            List<UnitOfMeasure> allUnits,
            List<UnitConversion> customConversions
    ) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException("INVALID_UNIT_CONVERSION", "Quantity must be non-negative");
        }
        if (fromUnitId == null || toUnitId == null) {
            throw new DomainException("INVALID_UNIT_CONVERSION", "Both source and target units are required");
        }
        if (fromUnitId.equals(toUnitId)) {
            return quantity.setScale(HIGH_PRECISION_SCALE, RoundingMode.HALF_UP);
        }

        Map<UUID, UnitOfMeasure> unitMap = new HashMap<>();
        if (allUnits != null) {
            for (UnitOfMeasure u : allUnits) {
                unitMap.put(u.id(), u);
            }
        }
        UnitOfMeasure fromUnit = unitMap.get(fromUnitId);
        UnitOfMeasure toUnit = unitMap.get(toUnitId);
        if (fromUnit == null || toUnit == null) {
            throw new DomainException("INVALID_UNIT_CONVERSION", "Unknown unit of measure");
        }

        // 1. Check direct item-specific custom conversion or reverse item-specific conversion first
        if (inventoryItemId != null && customConversions != null) {
            BigDecimal directItem = findGraphFactor(fromUnitId, toUnitId, inventoryItemId, true, customConversions);
            if (directItem != null) {
                return quantity.multiply(directItem, MC).setScale(HIGH_PRECISION_SCALE, RoundingMode.HALF_UP);
            }
        }

        // 2. Check tenant-level custom conversions (allowing package box/pack -> piece/kg if explicitly configured)
        if (customConversions != null) {
            BigDecimal tenantFactor = findGraphFactor(fromUnitId, toUnitId, inventoryItemId, false, customConversions);
            if (tenantFactor != null) {
                return quantity.multiply(tenantFactor, MC).setScale(HIGH_PRECISION_SCALE, RoundingMode.HALF_UP);
            }
        }

        // 3. Built-in dimensional conversion via shared baseUnit within compatible unitType
        if (!fromUnit.unitType().equals(toUnit.unitType())) {
            throw new DomainException(
                    "INCOMPATIBLE_UNIT_DIMENSIONS",
                    "Cannot convert incompatible dimensions from " + fromUnit.code() + " (" + fromUnit.unitType()
                            + ") to " + toUnit.code() + " (" + toUnit.unitType() + ")"
            );
        }

        FactorAnchor fromAnchor = resolveRootAnchor(fromUnit, unitMap);
        FactorAnchor toAnchor = resolveRootAnchor(toUnit, unitMap);
        if (!fromAnchor.rootUnitId().equals(toAnchor.rootUnitId())) {
            throw new DomainException(
                    "INVALID_UNIT_CONVERSION",
                    "No conversion path configured from " + fromUnit.code() + " to " + toUnit.code()
            );
        }

        // amount_in_root = quantity * fromAnchor.factorToRoot; amount_in_target = amount_in_root / toAnchor.factorToRoot
        BigDecimal inRoot = quantity.multiply(fromAnchor.factorToRoot(), MC);
        return inRoot.divide(toAnchor.factorToRoot(), HIGH_PRECISION_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal findGraphFactor(
            UUID fromUnitId,
            UUID toUnitId,
            UUID inventoryItemId,
            boolean itemOnly,
            List<UnitConversion> conversions
    ) {
        Map<UUID, Map<UUID, BigDecimal>> adj = new HashMap<>();
        for (UnitConversion c : conversions) {
            if (!c.active()) {
                continue;
            }
            if (itemOnly) {
                if (inventoryItemId == null || !inventoryItemId.equals(c.inventoryItemId())) {
                    continue;
                }
            } else if (c.inventoryItemId() != null && !c.inventoryItemId().equals(inventoryItemId)) {
                continue;
            }
            adj.computeIfAbsent(c.fromUnitId(), k -> new HashMap<>()).put(c.toUnitId(), c.conversionFactor());
            adj.computeIfAbsent(c.toUnitId(), k -> new HashMap<>())
                    .put(c.fromUnitId(), BigDecimal.ONE.divide(c.conversionFactor(), 12, RoundingMode.HALF_UP));
        }

        Deque<NodeFactor> queue = new ArrayDeque<>();
        Set<UUID> visited = new HashSet<>();
        queue.add(new NodeFactor(fromUnitId, BigDecimal.ONE));
        visited.add(fromUnitId);
        while (!queue.isEmpty()) {
            NodeFactor cur = queue.poll();
            if (cur.unitId().equals(toUnitId)) {
                return cur.factor();
            }
            Map<UUID, BigDecimal> neighbors = adj.get(cur.unitId());
            if (neighbors == null) {
                continue;
            }
            for (Map.Entry<UUID, BigDecimal> e : neighbors.entrySet()) {
                if (visited.add(e.getKey())) {
                    queue.add(new NodeFactor(e.getKey(), cur.factor().multiply(e.getValue(), MC)));
                }
            }
        }
        return null;
    }

    private static FactorAnchor resolveRootAnchor(UnitOfMeasure unit, Map<UUID, UnitOfMeasure> unitMap) {
        BigDecimal factor = BigDecimal.ONE;
        UnitOfMeasure current = unit;
        Set<UUID> seen = new HashSet<>();
        while (current.baseUnitId() != null && seen.add(current.id())) {
            BigDecimal step = current.conversionFactor() != null && current.conversionFactor().compareTo(BigDecimal.ZERO) > 0
                    ? current.conversionFactor()
                    : BigDecimal.ONE;
            factor = factor.multiply(step, MC);
            UnitOfMeasure parent = unitMap.get(current.baseUnitId());
            if (parent == null) {
                break;
            }
            current = parent;
        }
        return new FactorAnchor(current.id(), factor);
    }

    private record FactorAnchor(UUID rootUnitId, BigDecimal factorToRoot) {
    }

    private record NodeFactor(UUID unitId, BigDecimal factor) {
    }
}
