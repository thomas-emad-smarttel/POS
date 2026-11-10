package com.restaurantpos.domain.exception;

/**
 * Thrown when optimistic locking detects a concurrent update to an aggregate.
 */
public class ConcurrentModificationDomainException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ConcurrentModificationDomainException(String message, Throwable cause) {
        super("CONCURRENT_MODIFICATION", message, cause);
    }
}
