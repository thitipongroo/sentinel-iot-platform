package com.sentinel.iot.websocket;

import com.sentinel.iot.service.JwtService;
import com.sentinel.iot.service.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

/**
 * Validates the JWT token during the WebSocket upgrade handshake.
 *
 * <p>The client passes the access token as a query parameter:
 * {@code ws://host/ws/telemetry?token=<jwt>}.  This is the standard approach
 * because the WebSocket upgrade request cannot carry an Authorization header
 * in all browser environments.</p>
 *
 * <p>On successful validation the tenant UUID is stored as the session attribute
 * {@code "orgId"} so {@link TelemetryWebSocketHandler} can filter broadcasts
 * to only sessions belonging to the same tenant.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    static final String ORG_ID_ATTR = "orgId";

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            log.warn("WebSocket handshake rejected: not a servlet request");
            return false;
        }

        String token = servletRequest.getServletRequest().getParameter("token");
        if (token == null || token.isBlank()) {
            log.warn("WebSocket handshake rejected: missing token query parameter");
            return false;
        }

        try {
            String username = jwtService.extractUsername(token);
            var userDetails = userDetailsService.loadUserByUsername(username);
            if (!jwtService.isTokenValid(token, userDetails)) {
                log.warn("WebSocket handshake rejected: invalid or revoked token for user={}", username);
                return false;
            }
            UUID orgId = jwtService.extractOrgId(token);
            attributes.put(ORG_ID_ATTR, orgId);
            log.debug("WebSocket handshake accepted: user={} org={}", username, orgId);
            return true;
        } catch (Exception e) {
            log.warn("WebSocket handshake rejected: token validation failed — {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
    }
}
