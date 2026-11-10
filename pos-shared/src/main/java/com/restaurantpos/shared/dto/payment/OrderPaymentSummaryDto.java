package com.restaurantpos.shared.dto.payment;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderPaymentSummaryDto(
        UUID orderId,
        String orderNumber,
        String orderStatus,
        String currencyCode,
        BigDecimal orderTotal,
        BigDecimal netPaidAmount,
        BigDecimal totalRefundedAmount,
        BigDecimal remainingDue,
        List<PaymentDto> payments,
        List<PaymentAttemptDto> attempts,
        List<RefundDto> refunds,
        PaymentAttemptDto latestAttempt,
        ReceiptDto receipt,
        String hardwareNotice
) {
}
