package com.restaurantpos.domain.payment;

/**
 * Explicit lifecycle states for payment attempts and settled payment summaries.
 */
public enum PaymentAttemptStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
    UNCERTAIN,
    PARTIALLY_REFUNDED,
    REFUNDED
}
