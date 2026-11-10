package com.restaurantpos.application.payment;

import java.math.BigDecimal;

/**
 * Configurable payment, refund threshold, and receipt printing defaults for Local Server.
 */
public record PaymentPolicyConfig(
        BigDecimal refundSupervisorThreshold,
        int defaultReceiptPaperWidth,
        String defaultReceiptEncoding,
        String defaultReceiptLanguage,
        boolean autoPrintOnPayment
) {
    public PaymentPolicyConfig {
        refundSupervisorThreshold = refundSupervisorThreshold != null
                ? refundSupervisorThreshold
                : new BigDecimal("25.00");
        defaultReceiptPaperWidth = defaultReceiptPaperWidth >= 32 ? defaultReceiptPaperWidth : 42;
        defaultReceiptEncoding = (defaultReceiptEncoding == null || defaultReceiptEncoding.isBlank())
                ? "UTF-8" : defaultReceiptEncoding;
        defaultReceiptLanguage = (defaultReceiptLanguage == null || defaultReceiptLanguage.isBlank())
                ? "BILINGUAL" : defaultReceiptLanguage;
    }

    public static PaymentPolicyConfig defaults() {
        return new PaymentPolicyConfig(new BigDecimal("25.00"), 42, "UTF-8", "BILINGUAL", true);
    }
}
