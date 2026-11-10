package com.restaurantpos.domain.inventory;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Weighted Average Cost (WAC) calculator using BigDecimal HALF_UP precision.
 * Costs are recorded on ledger snapshots so historical orders and movements never change retroactively.
 */
public final class CostingService {

    private static final MathContext MC = new MathContext(18, RoundingMode.HALF_UP);
    public static final int COST_SCALE = 4;

    private CostingService() {
    }

    /**
     * Recomputes weighted average cost when stock increases with a known positive receipt/adjustment unit cost.
     * Negative or zero prior balance resets the average cost cleanly to the incoming receipt cost.
     */
    public static BigDecimal computeWeightedAverageCost(
            BigDecimal currentOnHand,
            BigDecimal currentAverageCost,
            BigDecimal addedQuantity,
            BigDecimal incomingUnitCost
    ) {
        BigDecimal prevQty = currentOnHand != null && currentOnHand.compareTo(BigDecimal.ZERO) > 0
                ? currentOnHand
                : BigDecimal.ZERO;
        BigDecimal prevCost = currentAverageCost != null && currentAverageCost.compareTo(BigDecimal.ZERO) >= 0
                ? currentAverageCost
                : BigDecimal.ZERO;
        BigDecimal addQty = addedQuantity != null && addedQuantity.compareTo(BigDecimal.ZERO) > 0
                ? addedQuantity
                : BigDecimal.ZERO;
        BigDecimal inCost = incomingUnitCost != null && incomingUnitCost.compareTo(BigDecimal.ZERO) >= 0
                ? incomingUnitCost
                : prevCost;

        if (addQty.compareTo(BigDecimal.ZERO) == 0) {
            return prevCost.setScale(COST_SCALE, RoundingMode.HALF_UP);
        }
        if (prevQty.compareTo(BigDecimal.ZERO) == 0) {
            return inCost.setScale(COST_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal totalValue = prevQty.multiply(prevCost, MC).add(addQty.multiply(inCost, MC), MC);
        BigDecimal totalQty = prevQty.add(addQty, MC);
        return totalValue.divide(totalQty, COST_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal computeFoodCostPercentage(BigDecimal unitRecipeCost, BigDecimal sellingPrice) {
        if (unitRecipeCost == null || sellingPrice == null || sellingPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return unitRecipeCost.multiply(BigDecimal.valueOf(100), MC)
                .divide(sellingPrice, 2, RoundingMode.HALF_UP);
    }
}
