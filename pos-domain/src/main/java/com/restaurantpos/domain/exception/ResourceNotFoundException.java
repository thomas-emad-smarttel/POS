package com.restaurantpos.domain.exception;

/**
 * Thrown when an expected aggregate or scoped record does not exist.
 */
public class ResourceNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", message);
    }
}
