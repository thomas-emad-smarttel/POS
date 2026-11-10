package com.restaurantpos.domain.exception;

/**
 * Thrown when persistence rejects an operation due to foreign key, check, or immutability constraints.
 */
public class DataIntegrityDomainException extends DomainException {

    private static final long serialVersionUID = 1L;

    public DataIntegrityDomainException(String message, Throwable cause) {
        super("DATA_INTEGRITY_VIOLATION", message, cause);
    }
}
