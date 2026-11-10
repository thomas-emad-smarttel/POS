package com.restaurantpos.domain.port;

import java.math.BigDecimal;

/**
 * Provider-independent external payment terminal abstraction.
 */
public interface PaymentTerminalPort {

    enum Outcome {
        APPROVED,
        DECLINED,
        UNCERTAIN_TIMEOUT,
        CANCELLED,
        REVERSED
    }

    record StartCommand(
            String idempotencyKey,
            String currencyCode,
            BigDecimal amount,
            String terminalRef,
            String simulationHint
    ) {
    }

    record TerminalResult(
            Outcome outcome,
            String providerName,
            String providerTxId,
            String terminalRef,
            String maskedPan,
            String cardBrand,
            String authCode,
            String responseCode,
            String message
    ) {
    }

    TerminalResult startTransaction(StartCommand command);

    TerminalResult cancelTransaction(String idempotencyKey, String providerTxId, String terminalRef);
}
