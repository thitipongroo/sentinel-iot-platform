package com.sentinel.iot.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@Slf4j
public class TelemetryWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket connected: {} | total={}", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket disconnected: {} | total={}", session.getId(), sessions.size());
    }

    public void broadcast(String message) {
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
