package com.restaurantpos.domain.port;

import java.math.BigDecimal;

public interface PaymentReversalPort {
    PaymentTerminalPort.TerminalResult reverse(String providerTxId, BigDecimal amount, String currencyCode, String simulationHint);
}
