package com.restaurantpos.domain.order;

import com.restaurantpos.shared.model.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Authoritative server-side monetary calculator using {@link Money} and {@link BigDecimal}
 * with {@link RoundingMode#HALF_UP} (currency scale 2). Never uses floating-point arithmetic.
 */
public final class PricingCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int CALC_SCALE = 6;
    private static final int MONEY_SCALE = Money.DEFAULT_SCALE;

    public record LineInput(
            UUID lineId,
            BigDecimal baseUnitPrice,
            BigDecimal modifierUnitTotal,
            BigDecimal quantity,
            UUID taxId,
            String taxName,
            BigDecimal taxRatePercent,
            boolean taxInclusive,
            BigDecimal itemDiscountAmount
    ) {
    }

    public record OrderDiscountInput(
            String discountType,
            BigDecimal discountValue
    ) {
    }

    public record ComputedLine(
            UUID lineId,
            BigDecimal grossLineAmount,
            BigDecimal itemDiscountAmount,
            BigDecimal taxAmount,
            BigDecimal lineTotal
    ) {
    }

    public record ComputedTaxSummary(
            UUID taxId,
            String taxName,
            BigDecimal ratePercent,
            boolean inclusive,
            BigDecimal taxableAmount,
            BigDecimal taxAmount
    ) {
    }

    public record OrderCalculationResult(
            Money subtotal,
            Money discountTotal,
            Money taxTotal,
            Money grandTotal,
            List<ComputedLine> lines,
            List<ComputedTaxSummary> taxSummaries
    ) {
    }

    private PricingCalculator() {
    }

    public static OrderCalculationResult calculate(
            String currencyCode,
            List<LineInput> inputs,
            List<OrderDiscountInput> orderLevelDiscounts
    ) {
        Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        Money subtotal = Money.zero(currencyCode);
        Money totalItemDiscount = Money.zero(currencyCode);

        List<ComputedLineDraft> drafts = new ArrayList<>();
        for (LineInput in : inputs) {
            BigDecimal unitWithMods = in.baseUnitPrice().add(in.modifierUnitTotal());
            BigDecimal lineGross = unitWithMods.multiply(in.quantity()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal itemDisc = in.itemDiscountAmount() != null
                    ? in.itemDiscountAmount().min(lineGross).max(BigDecimal.ZERO).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal lineNetAfterItemDisc = lineGross.subtract(itemDisc);

            subtotal = subtotal.add(Money.of(lineGross, currencyCode));
            totalItemDiscount = totalItemDiscount.add(Money.of(itemDisc, currencyCode));
            drafts.add(new ComputedLineDraft(in, lineGross, itemDisc, lineNetAfterItemDisc));
        }

        BigDecimal netAfterItemDiscounts = subtotal.amount().subtract(totalItemDiscount.amount());
        BigDecimal orderDiscountTotal = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        if (orderLevelDiscounts != null && netAfterItemDiscounts.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal remaining = netAfterItemDiscounts;
            for (OrderDiscountInput disc : orderLevelDiscounts) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BigDecimal applied;
                if ("PERCENTAGE".equals(disc.discountType())) {
                    applied = netAfterItemDiscounts.multiply(disc.discountValue())
                            .divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
                } else {
                    applied = disc.discountValue().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                }
                applied = applied.min(remaining).max(BigDecimal.ZERO);
                orderDiscountTotal = orderDiscountTotal.add(applied);
                remaining = remaining.subtract(applied);
            }
        }

        // Pro-rate order-level discount across lines proportionally for accurate per-tax-bucket calculation
        List<ComputedLine> computedLines = new ArrayList<>();
        Map<String, TaxAccumulator> taxBuckets = new LinkedHashMap<>();
        BigDecimal distributedOrderDiscount = BigDecimal.ZERO;

        for (int i = 0; i < drafts.size(); i++) {
            ComputedLineDraft d = drafts.get(i);
            BigDecimal lineOrderDisc = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            if (orderDiscountTotal.compareTo(BigDecimal.ZERO) > 0 && netAfterItemDiscounts.compareTo(BigDecimal.ZERO) > 0) {
                if (i == drafts.size() - 1) {
                    lineOrderDisc = orderDiscountTotal.subtract(distributedOrderDiscount).min(d.netAfterItemDisc());
                } else {
                    lineOrderDisc = orderDiscountTotal.multiply(d.netAfterItemDisc())
                            .divide(netAfterItemDiscounts, MONEY_SCALE, RoundingMode.HALF_UP);
                    distributedOrderDiscount = distributedOrderDiscount.add(lineOrderDisc);
                }
            }

            BigDecimal taxableOrInclusiveBase = d.netAfterItemDisc().subtract(lineOrderDisc).max(BigDecimal.ZERO);
            BigDecimal rate = d.input().taxRatePercent() != null ? d.input().taxRatePercent() : BigDecimal.ZERO;
            BigDecimal lineTax;
            BigDecimal lineTotal;
            BigDecimal taxableNet;

            if (rate.compareTo(BigDecimal.ZERO) <= 0) {
                lineTax = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                taxableNet = taxableOrInclusiveBase;
                lineTotal = taxableOrInclusiveBase;
            } else if (d.input().taxInclusive()) {
                BigDecimal divisor = HUNDRED.add(rate);
                lineTax = taxableOrInclusiveBase.multiply(rate)
                        .divide(divisor, CALC_SCALE, RoundingMode.HALF_UP)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                taxableNet = taxableOrInclusiveBase.subtract(lineTax);
                lineTotal = taxableOrInclusiveBase;
            } else {
                lineTax = taxableOrInclusiveBase.multiply(rate)
                        .divide(HUNDRED, CALC_SCALE, RoundingMode.HALF_UP)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                taxableNet = taxableOrInclusiveBase;
                lineTotal = taxableOrInclusiveBase.add(lineTax);
            }

            computedLines.add(new ComputedLine(
                    d.input().lineId(),
                    d.gross(),
                    d.itemDiscount(),
                    lineTax,
                    lineTotal
            ));

            if (d.input().taxId() != null && rate.compareTo(BigDecimal.ZERO) > 0) {
                String bucketKey = d.input().taxId() + ":" + d.input().taxInclusive();
                TaxAccumulator acc = taxBuckets.computeIfAbsent(bucketKey, k -> new TaxAccumulator(
                        d.input().taxId(),
                        d.input().taxName(),
                        rate,
                        d.input().taxInclusive()
                ));
                acc.add(taxableNet, lineTax);
            }
        }

        Money totalDiscount = totalItemDiscount.add(Money.of(orderDiscountTotal, currencyCode));
        Money totalTax = Money.zero(currencyCode);
        Money grandTotal = Money.zero(currencyCode);
        for (ComputedLine line : computedLines) {
            totalTax = totalTax.add(Money.of(line.taxAmount(), currencyCode));
            grandTotal = grandTotal.add(Money.of(line.lineTotal(), currencyCode));
        }

        List<ComputedTaxSummary> taxSummaries = taxBuckets.values().stream()
                .map(TaxAccumulator::toSummary)
                .toList();

        return new OrderCalculationResult(subtotal, totalDiscount, totalTax, grandTotal, computedLines, taxSummaries);
    }

    private record ComputedLineDraft(
            LineInput input,
            BigDecimal gross,
            BigDecimal itemDiscount,
            BigDecimal netAfterItemDisc
    ) {
    }

    private static final class TaxAccumulator {
        private final UUID taxId;
        private final String taxName;
        private final BigDecimal rate;
        private final boolean inclusive;
        private BigDecimal taxable = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        private BigDecimal tax = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        private TaxAccumulator(UUID taxId, String taxName, BigDecimal rate, boolean inclusive) {
            this.taxId = taxId;
            this.taxName = taxName;
            this.rate = rate;
            this.inclusive = inclusive;
        }

        private void add(BigDecimal taxableAdd, BigDecimal taxAdd) {
            this.taxable = this.taxable.add(taxableAdd);
            this.tax = this.tax.add(taxAdd);
        }

        private ComputedTaxSummary toSummary() {
            return new ComputedTaxSummary(taxId, taxName, rate, inclusive, taxable, tax);
        }
    }
}
