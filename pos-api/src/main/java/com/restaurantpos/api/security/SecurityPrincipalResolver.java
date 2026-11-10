package com.restaurantpos.api.security;

import com.restaurantpos.api.filter.CorrelationIdFilter;
import com.restaurantpos.domain.exception.AuthenticationDomainException;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.shared.model.CorrelationId;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Helper resolving the authenticated {@link AccessPrincipal} and current {@link CorrelationId} for controllers.
 */
public final class SecurityPrincipalResolver {

    private SecurityPrincipalResolver() {
    }

    public static AccessPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AccessPrincipal principal)) {
            throw new AuthenticationDomainException("AUTH_REQUIRED", "Authentication is required");
        }
        return principal;
    }

    public static CorrelationId currentCorrelationId() {
        String raw = MDC.get(CorrelationIdFilter.MDC_KEY);
        return (raw != null && !raw.isBlank()) ? CorrelationId.of(raw) : CorrelationId.generate();
    }
}
