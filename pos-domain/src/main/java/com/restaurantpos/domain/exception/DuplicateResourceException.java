package com.restaurantpos.domain.exception;

/**
 * Thrown when a unique business identifier or idempotency constraint is violated.
 */
public class DuplicateResourceException extends DomainException {

    private static final long serialVersionUID = 1L;

    public DuplicateResourceException(String message, Throwable cause) {
        super("DUPLICATE_RESOURCE", message, cause);
    }
}
