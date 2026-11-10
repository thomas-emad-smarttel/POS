package com.restaurantpos.shared.dto.payment;

import java.util.List;

public record ReconciliationReportDto(
        List<PaymentAttemptDto> pendingOrUncertainAttempts,
        List<PaymentAttemptDto> mismatchAttempts,
        List<RefundDto> failedRefunds,
        List<CashSessionDto> varianceSessions
) {
}
