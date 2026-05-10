package com.sentinel.iot.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Manages WebSocket sessions for THIS backend instance only.
 *
 * Cross-instance broadcast is handled by Redis pub/sub (see WebSocketBroadcastPublisher /
 * WebSocketBroadcastSubscriber). Every instance that receives a message on the Redis channel
 * calls broadcastLocal(), which fans it out to the sessions connected to that instance.
 *
 * Sticky sessions at the load balancer (ip_hash / consistent hashing) are required so that
 * WebSocket upgrade handshakes and subsequent frames always reach the same backend instance.
 * See deploy/nginx-lb.conf for the reference nginx configuration.
 */
@Component
@Slf4j
public class TelemetryWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket connected: {} | local={}", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket disconnected: {} | local={}", session.getId(), sessions.size());
    }

    /**
     * Sends a message to all WebSocket sessions connected to THIS instance.
     * Called by WebSocketBroadcastSubscriber when a message arrives on the Redis channel.
     */
    public void broadcastLocal(String message) {
        if (sessions.isEmpty()) {
            return;
        }
        TextMessage textMessage = new TextMessage(message);
        sessions.removeIf(session -> !session.isOpen());
        sessions.forEach(session -> {
            try {
                session.sendMessage(textMessage);
            } catch (Exception e) {
                log.warn("Failed to send WebSocket message to {}: {}", session.getId(), e.getMessage());
            }
        });
    }
}
