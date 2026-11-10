package com.restaurantpos.devices.payment;

import java.math.BigDecimal;

/**
 * Provider-independent external card terminal abstraction.
 * Stores/returns only safe non-sensitive references (never full PAN, CVV, PIN, or track data).
 */
public interface CardTerminalGateway {

    enum TerminalOutcome {
        APPROVED,
        DECLINED,
        UNCERTAIN_TIMEOUT,
        CANCELLED,
        REVERSED
    }

    record StartPaymentCommand(
            String idempotencyKey,
            String currencyCode,
            BigDecimal amount,
            String terminalRef,
            String simulationHint
    ) {
    }

    record TerminalTransactionResult(
            TerminalOutcome outcome,
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

    TerminalTransactionResult startTransaction(StartPaymentCommand command);

    TerminalTransactionResult queryTransactionStatus(String idempotencyKey, String providerTxId, String terminalRef);

    TerminalTransactionResult cancelTransaction(String idempotencyKey, String providerTxId, String terminalRef);

    TerminalTransactionResult reverseTransaction(String providerTxId, BigDecimal amount, String currencyCode, String simulationHint);
}
