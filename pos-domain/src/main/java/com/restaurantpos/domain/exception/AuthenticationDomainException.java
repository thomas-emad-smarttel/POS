package com.restaurantpos.domain.exception;

/**
 * Thrown when authentication fails (bad credentials, locked account, expired or revoked session).
 */
public class AuthenticationDomainException extends DomainException {

    private static final long serialVersionUID = 1L;

    public AuthenticationDomainException(String errorCode, String message) {
        super(errorCode, message);
    }
}
