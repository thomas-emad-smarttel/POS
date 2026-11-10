package com.restaurantpos.api.exception;

import com.restaurantpos.api.filter.CorrelationIdFilter;
import com.restaurantpos.domain.exception.AuthenticationDomainException;
import com.restaurantpos.domain.exception.ConcurrentModificationDomainException;
import com.restaurantpos.domain.exception.DomainException;
import com.restaurantpos.domain.exception.DuplicateResourceException;
import com.restaurantpos.domain.exception.ResourceNotFoundException;
import com.restaurantpos.domain.exception.UnauthorizedOperationException;
import com.restaurantpos.shared.dto.ApiError;
import com.restaurantpos.shared.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Translates domain, authentication, authorization, and validation exceptions into sanitized API error envelopes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthenticationDomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationDomainException ex) {
        String correlationId = currentCorrelationId();
        LOG.warn("Authentication failure correlationId={} code={}", correlationId, ex.errorCode());
        HttpStatus status = switch (ex.errorCode()) {
            case "AUTH_ACCOUNT_LOCKED" -> HttpStatus.LOCKED;
            case "AUTH_THROTTLED" -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.UNAUTHORIZED;
        };
        return ResponseEntity.status(status)
                .body(ApiResponse.fail(ApiError.of(ex.errorCode(), ex.getMessage(), correlationId)));
    }

    @ExceptionHandler(UnauthorizedOperationException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedOperationException ex) {
        String correlationId = currentCorrelationId();
        LOG.warn("Authorization denied correlationId={} code={}", correlationId, ex.errorCode());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ApiError.of(ex.errorCode(), ex.getMessage(), correlationId)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleSpringAccessDenied(AccessDeniedException ex) {
        String correlationId = currentCorrelationId();
        LOG.warn("Spring Security access denied correlationId={}", correlationId);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ApiError.of("AUTH_FORBIDDEN", "You do not have permission to perform this action", correlationId)));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        String correlationId = currentCorrelationId();
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ApiError.of(ex.errorCode(), ex.getMessage(), correlationId)));
    }

    @ExceptionHandler({DuplicateResourceException.class, ConcurrentModificationDomainException.class})
    public ResponseEntity<ApiResponse<Void>> handleConflict(DomainException ex) {
        String correlationId = currentCorrelationId();
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ApiError.of(ex.errorCode(), ex.getMessage(), correlationId)));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(DomainException ex) {
        String correlationId = currentCorrelationId();
        LOG.warn("Domain rule violation correlationId={} code={}", correlationId, ex.errorCode());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.fail(ApiError.of(ex.errorCode(), ex.getMessage(), correlationId)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String correlationId = currentCorrelationId();
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ApiError.of("VALIDATION_ERROR", "Request validation failed", correlationId, details)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        String correlationId = currentCorrelationId();
        LOG.error("Unhandled server exception correlationId={}", correlationId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ApiError.of("INTERNAL_ERROR", "An unexpected error occurred", correlationId)));
    }

    private static String currentCorrelationId() {
        String value = MDC.get(CorrelationIdFilter.MDC_KEY);
        return value != null ? value : "unavailable";
    }
}
