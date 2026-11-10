package com.restaurantpos.domain.port;

public interface PaymentAuthorizationPort {
    PaymentTerminalPort.TerminalResult authorize(PaymentTerminalPort.StartCommand command);
}
