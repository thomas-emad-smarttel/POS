package com.restaurantpos.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantpos.api.filter.CorrelationIdFilter;
import com.restaurantpos.application.security.AuthenticationService;
import com.restaurantpos.domain.exception.AuthenticationDomainException;
import com.restaurantpos.domain.exception.UnauthorizedOperationException;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.shared.dto.ApiError;
import com.restaurantpos.shared.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates opaque bearer tokens against persisted {@code user_sessions} and populates live RBAC authorities.
 */
@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ObjectProvider<AuthenticationService> authenticationServiceProvider;
    private final ObjectMapper objectMapper;

    public SessionAuthenticationFilter(
            ObjectProvider<AuthenticationService> authenticationServiceProvider,
            ObjectMapper objectMapper
    ) {
        this.authenticationServiceProvider = authenticationServiceProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        AuthenticationService authService = authenticationServiceProvider.getIfAvailable();
        if (authService == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawToken = header.substring(BEARER_PREFIX.length()).trim();
        try {
            AccessPrincipal principal = authService.authenticateToken(rawToken);
            List<GrantedAuthority> authorities = new ArrayList<>();
            principal.effectivePermissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p.code())));
            principal.assignedRoles().forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (AuthenticationDomainException ex) {
            SecurityContextHolder.clearContext();
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ex.errorCode(), ex.getMessage());
        } catch (UnauthorizedOperationException ex) {
            SecurityContextHolder.clearContext();
            writeError(response, HttpServletResponse.SC_FORBIDDEN, ex.errorCode(), ex.getMessage());
        }
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        ApiResponse<Void> body = ApiResponse.fail(ApiError.of(code, message, correlationId));
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
