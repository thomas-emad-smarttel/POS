package com.restaurantpos.devices.payment;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Virtual payment terminal adapter for automated testing and non-production simulation.
 * Supports deterministic approvals, declines, timeouts, status queries, cancellations, and reversals.
 */
public class VirtualCardTerminalGateway implements CardTerminalGateway {

    private static final String PROVIDER_NAME = "VIRTUAL_EMV_TERMINAL";

    private final Map<String, TerminalTransactionResult> ledgerByIdempotencyKey = new ConcurrentHashMap<>();
    private final Map<String, TerminalOutcome> deferredRecoveryOutcome = new ConcurrentHashMap<>();
    private volatile TerminalOutcome defaultNextOutcome = TerminalOutcome.APPROVED;

    public void setDefaultNextOutcome(TerminalOutcome outcome) {
        this.defaultNextOutcome = outcome == null ? TerminalOutcome.APPROVED : outcome;
    }

    public void scheduleDeferredOutcome(String idempotencyKey, TerminalOutcome outcome) {
        deferredRecoveryOutcome.put(idempotencyKey, outcome);
    }

    @Override
    public TerminalTransactionResult startTransaction(StartPaymentCommand command) {
        TerminalTransactionResult existing = ledgerByIdempotencyKey.get(command.idempotencyKey());
        if (existing != null) {
            return existing;
        }

        String hint = command.simulationHint() != null ? command.simulationHint().trim().toUpperCase() : "";
        String txId = "VTX-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        String terminalRef = command.terminalRef() != null && !command.terminalRef().isBlank()
                ? command.terminalRef()
                : "VTERM-01";

        TerminalOutcome outcome = resolveStartOutcome(hint);
        if ("TIMEOUT_THEN_APPROVED".equals(hint)) {
            outcome = TerminalOutcome.UNCERTAIN_TIMEOUT;
            deferredRecoveryOutcome.put(command.idempotencyKey(), TerminalOutcome.APPROVED);
        } else if ("TIMEOUT_THEN_DECLINED".equals(hint)) {
            outcome = TerminalOutcome.UNCERTAIN_TIMEOUT;
            deferredRecoveryOutcome.put(command.idempotencyKey(), TerminalOutcome.DECLINED);
        }

        TerminalTransactionResult result = switch (outcome) {
            case APPROVED -> new TerminalTransactionResult(
                    TerminalOutcome.APPROVED, PROVIDER_NAME, txId, terminalRef,
                    "**** **** **** 4242", "VISA", "AUTH" + txId.substring(4, 10), "00", "Approved (virtual terminal)"
            );
            case DECLINED -> new TerminalTransactionResult(
                    TerminalOutcome.DECLINED, PROVIDER_NAME, txId, terminalRef,
                    "**** **** **** 4242", "VISA", null, "05", "Do not honor (virtual decline)"
            );
            case CANCELLED -> new TerminalTransactionResult(
                    TerminalOutcome.CANCELLED, PROVIDER_NAME, txId, terminalRef,
                    null, null, null, "17", "Cancelled on virtual terminal"
            );
            case UNCERTAIN_TIMEOUT -> new TerminalTransactionResult(
                    TerminalOutcome.UNCERTAIN_TIMEOUT, PROVIDER_NAME, txId, terminalRef,
                    null, null, null, "TO", "Terminal response timed out; status query required"
            );
            case REVERSED -> new TerminalTransactionResult(
                    TerminalOutcome.REVERSED, PROVIDER_NAME, txId, terminalRef,
                    "**** **** **** 4242", "VISA", null, "00", "Reversed (virtual terminal)"
            );
        };

        ledgerByIdempotencyKey.put(command.idempotencyKey(), result);
        return result;
    }

    @Override
    public TerminalTransactionResult queryTransactionStatus(String idempotencyKey, String providerTxId, String terminalRef) {
        TerminalOutcome recovery = deferredRecoveryOutcome.remove(idempotencyKey);
        TerminalTransactionResult previous = ledgerByIdempotencyKey.get(idempotencyKey);
        String txId = providerTxId != null ? providerTxId : (previous != null ? previous.providerTxId() : "VTX-UNKNOWN");
        String tRef = terminalRef != null ? terminalRef : (previous != null ? previous.terminalRef() : "VTERM-01");

        if (recovery != null) {
            TerminalTransactionResult resolved = recovery == TerminalOutcome.APPROVED
                    ? new TerminalTransactionResult(
                            TerminalOutcome.APPROVED, PROVIDER_NAME, txId, tRef,
                            "**** **** **** 4242", "VISA", "AUTH" + txId.substring(Math.max(0, txId.length() - 6)), "00",
                            "Approved on terminal status query")
                    : new TerminalTransactionResult(
                            TerminalOutcome.DECLINED, PROVIDER_NAME, txId, tRef,
                            "**** **** **** 4242", "VISA", null, "05",
                            "Declined on terminal status query");
            ledgerByIdempotencyKey.put(idempotencyKey, resolved);
            return resolved;
        }
        if (previous != null) {
            return previous;
        }
        return new TerminalTransactionResult(
                TerminalOutcome.DECLINED, PROVIDER_NAME, txId, tRef,
                null, null, null, "12", "Transaction not found on virtual terminal"
        );
    }

    @Override
    public TerminalTransactionResult cancelTransaction(String idempotencyKey, String providerTxId, String terminalRef) {
        TerminalTransactionResult cancelled = new TerminalTransactionResult(
                TerminalOutcome.CANCELLED, PROVIDER_NAME, providerTxId, terminalRef,
                null, null, null, "17", "Cancelled by operator on virtual terminal"
        );
        if (idempotencyKey != null) {
            ledgerByIdempotencyKey.put(idempotencyKey, cancelled);
        }
        return cancelled;
    }

    @Override
    public TerminalTransactionResult reverseTransaction(String providerTxId, BigDecimal amount, String currencyCode, String simulationHint) {
        if (simulationHint != null && simulationHint.trim().toUpperCase().contains("REVERSE_FAIL")) {
            return new TerminalTransactionResult(
                    TerminalOutcome.DECLINED, PROVIDER_NAME, providerTxId, "VTERM-01",
                    null, null, null, "91", "Virtual reversal declined by host"
            );
        }
        return new TerminalTransactionResult(
                TerminalOutcome.REVERSED, PROVIDER_NAME, "REV-" + providerTxId, "VTERM-01",
                "**** **** **** 4242", "VISA", "REVOK1", "00", "Refund/reversal approved (virtual terminal)"
        );
    }

    private TerminalOutcome resolveStartOutcome(String hint) {
        return switch (hint) {
            case "DECLINED" -> TerminalOutcome.DECLINED;
            case "TIMEOUT", "UNCERTAIN" -> TerminalOutcome.UNCERTAIN_TIMEOUT;
            case "CANCELLED" -> TerminalOutcome.CANCELLED;
            case "APPROVED" -> TerminalOutcome.APPROVED;
            default -> defaultNextOutcome;
        };
    }
}
