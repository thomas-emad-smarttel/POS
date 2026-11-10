package com.restaurantpos.api.websocket;

import com.restaurantpos.application.security.AuthenticationService;
import com.restaurantpos.domain.security.AccessPrincipal;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Registers the authenticated branch WebSocket endpoint {@code /ws/events}.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketBranchEventHub eventHub;
    private final ObjectProvider<AuthenticationService> authenticationServiceProvider;

    public WebSocketConfig(
            WebSocketBranchEventHub eventHub,
            ObjectProvider<AuthenticationService> authenticationServiceProvider
    ) {
        this.eventHub = eventHub;
        this.authenticationServiceProvider = authenticationServiceProvider;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(eventHub, "/ws/events")
                .addInterceptors(new BearerHandshakeInterceptor(authenticationServiceProvider));
    }

    private static final class BearerHandshakeInterceptor implements HandshakeInterceptor {

        private final ObjectProvider<AuthenticationService> authProvider;

        private BearerHandshakeInterceptor(ObjectProvider<AuthenticationService> authProvider) {
            this.authProvider = authProvider;
        }

        @Override
        public boolean beforeHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler wsHandler,
                Map<String, Object> attributes
        ) {
            AuthenticationService authService = authProvider.getIfAvailable();
            if (authService == null) {
                response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                return false;
            }
            String authHeader = request.getHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            try {
                AccessPrincipal principal = authService.authenticateToken(authHeader.substring(7).trim());
                if (principal.activeBranchId() == null) {
                    response.setStatusCode(HttpStatus.FORBIDDEN);
                    return false;
                }
                attributes.put(WebSocketBranchEventHub.PRINCIPAL_ATTR, principal);
                return true;
            } catch (Exception ex) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
        }

        @Override
        public void afterHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler wsHandler,
                Exception exception
        ) {
        }
    }
}
