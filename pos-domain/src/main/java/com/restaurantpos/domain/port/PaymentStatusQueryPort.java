package com.restaurantpos.domain.port;

public interface PaymentStatusQueryPort {
    PaymentTerminalPort.TerminalResult queryStatus(String idempotencyKey, String providerTxId, String terminalRef);
}
