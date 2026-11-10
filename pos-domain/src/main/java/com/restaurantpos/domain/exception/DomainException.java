package com.restaurantpos.domain.exception;

/**
 * Base domain exception for business rule violations in pure domain logic.
 */
public class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final String errorCode;

    public DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DomainException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
