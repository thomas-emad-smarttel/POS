package com.restaurantpos.desktop.infra;

import java.io.IOException;

/**
 * Represents a structured API error returned by the Local Server (e.g., 401, 403, 409, 422).
 */
public class ApiClientException extends IOException {

    private static final long serialVersionUID = 1L;
    private final int statusCode;
    private final String errorCode;

    public ApiClientException(int statusCode, String errorCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public int statusCode() {
        return statusCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
