package com.sentinel.iot.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Manages WebSocket sessions for THIS backend instance only.
 *
 * Cross-instance broadcast is handled by Redis pub/sub (see WebSocketBroadcastPublisher /
 * WebSocketBroadcastSubscriber). Every instance that receives a message on the Redis channel
 * calls broadcastLocal(), which fans it out to the sessions belonging to the same tenant.
 *
 * Session tenant identity is established at handshake time by
 * {@link JwtWebSocketHandshakeInterceptor} and stored as the session attribute "orgId".
 *
 * Broadcast message format: {@code <orgId>|<rawPayload>}
 * The orgId prefix is stripped before forwarding to the client.
 *
 * Sticky sessions at the load balancer (ip_hash / consistent hashing) are required so that
 * WebSocket upgrade handshakes and subsequent frames always reach the same backend instance.
 * See deploy/nginx-lb.conf for the reference nginx configuration.
 */
@Component
@Slf4j
public class TelemetryWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @SuppressWarnings("null")
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        UUID orgId = (UUID) session.getAttributes().get(JwtWebSocketHandshakeInterceptor.ORG_ID_ATTR);
        log.info("WebSocket connected: {} org={} | local={}", session.getId(), orgId, sessions.size());
    }

    @SuppressWarnings("null")
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket disconnected: {} | local={}", session.getId(), sessions.size());
    }

    /**
     * Sends the payload to all WebSocket sessions for the matching tenant on THIS instance.
     * The message format is {@code <orgId>|<rawPayload>} — only the rawPayload is forwarded.
     */
    @SuppressWarnings("null")
    public void broadcastLocal(String message) {
        if (sessions.isEmpty()) {
            return;
        }
        int separatorIndex = message.indexOf('|');
        if (separatorIndex < 0) {
            log.warn("Dropping malformed broadcast message: missing orgId prefix");
            return;
        }
        String orgIdStr  = message.substring(0, separatorIndex);
        String payload   = message.substring(separatorIndex + 1);
        TextMessage textMessage = new TextMessage(payload);

        sessions.removeIf(session -> !session.isOpen());
        sessions.forEach(session -> {
            UUID sessionOrg = (UUID) session.getAttributes().get(JwtWebSocketHandshakeInterceptor.ORG_ID_ATTR);
            if (sessionOrg == null || !sessionOrg.toString().equals(orgIdStr)) {
                return; // cross-tenant — skip
            }
            try {
                session.sendMessage(textMessage);
            } catch (Exception e) {
                log.warn("Failed to send WebSocket message to {}: {}", session.getId(), e.getMessage());
            }
        });
    }
}
