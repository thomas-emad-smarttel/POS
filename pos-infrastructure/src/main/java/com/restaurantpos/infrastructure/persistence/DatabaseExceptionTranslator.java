package com.restaurantpos.infrastructure.persistence;

import com.restaurantpos.domain.exception.ConcurrentModificationDomainException;
import com.restaurantpos.domain.exception.DataIntegrityDomainException;
import com.restaurantpos.domain.exception.DuplicateResourceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.function.Supplier;

/**
 * Translates Spring/JPA persistence exceptions into clean domain exceptions without leaking SQL internals.
 */
@Component
public class DatabaseExceptionTranslator {

    private static final String SQLSTATE_UNIQUE_VIOLATION = "23505";

    public <T> T execute(String operationContext, Supplier<T> action) {
        try {
            return action.get();
        } catch (OptimisticLockingFailureException ex) {
            throw new ConcurrentModificationDomainException(
                    "Concurrent modification detected during " + operationContext,
                    ex
            );
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueViolation(ex)) {
                throw new DuplicateResourceException(
                        "Duplicate resource or idempotency conflict during " + operationContext,
                        ex
                );
            }
            throw new DataIntegrityDomainException(
                    "Database integrity constraint violated during " + operationContext,
                    ex
            );
        }
    }

    private static boolean isUniqueViolation(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof SQLException sqlEx && SQLSTATE_UNIQUE_VIOLATION.equals(sqlEx.getSQLState())) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }
}
