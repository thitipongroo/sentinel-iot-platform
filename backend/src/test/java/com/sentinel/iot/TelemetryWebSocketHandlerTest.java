package com.sentinel.iot;

import com.sentinel.iot.websocket.TelemetryWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("TelemetryWebSocketHandler")
class TelemetryWebSocketHandlerTest {

    private static final UUID   TEST_ORG      = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String BROADCAST_MSG = TEST_ORG + "|{\"temp\":42}";

    TelemetryWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TelemetryWebSocketHandler();
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Session lifecycle")
    class SessionLifecycle {

        @SuppressWarnings("null")
        @Test
        @DisplayName("newly connected session receives subsequent broadcasts")
        void afterConnectionEstablished_tracksSession() throws Exception {
            WebSocketSession session = openSession("s1");
            handler.afterConnectionEstablished(session);

            handler.broadcastLocal(BROADCAST_MSG);

            verify(session).sendMessage(any(TextMessage.class));
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("closed session no longer receives broadcasts after disconnect")
        void afterConnectionClosed_removesSession() throws Exception {
            WebSocketSession session = openSession("s1");
            handler.afterConnectionEstablished(session);
            handler.afterConnectionClosed(session, CloseStatus.NORMAL);

            handler.broadcastLocal(BROADCAST_MSG);

            verify(session, never()).sendMessage(any());
        }
    }

    // ── Broadcast routing ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Broadcast routing")
    class BroadcastRouting {

        @SuppressWarnings("null")
        @Test
        @DisplayName("delivers message to all open sessions belonging to the same org")
        void broadcast_sendsToAllOpenSessions() throws Exception {
            WebSocketSession s1 = openSession("s1");
            WebSocketSession s2 = openSession("s2");
            WebSocketSession s3 = openSession("s3");
            handler.afterConnectionEstablished(s1);
            handler.afterConnectionEstablished(s2);
            handler.afterConnectionEstablished(s3);

            handler.broadcastLocal(BROADCAST_MSG);

            verify(s1).sendMessage(any(TextMessage.class));
            verify(s2).sendMessage(any(TextMessage.class));
            verify(s3).sendMessage(any(TextMessage.class));
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("skips sessions whose isOpen() returns false")
        void broadcast_skipsClosedSessions() throws Exception {
            WebSocketSession open   = openSession("open");
            WebSocketSession closed = closedSession("closed");
            handler.afterConnectionEstablished(open);
            handler.afterConnectionEstablished(closed);

            handler.broadcastLocal(BROADCAST_MSG);

            verify(open).sendMessage(any(TextMessage.class));
            verify(closed, never()).sendMessage(any());
        }
    }

    // ── Resilience ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Resilience")
    class Resilience {

        @SuppressWarnings("null")
        @Test
        @DisplayName("continues broadcasting to remaining sessions when one send fails")
        void broadcast_continuesWhenOneSendFails() throws Exception {
            WebSocketSession good = openSession("good");
            WebSocketSession bad  = openSession("bad");
            doThrow(new RuntimeException("network error")).when(bad).sendMessage(any());
            handler.afterConnectionEstablished(good);
            handler.afterConnectionEstablished(bad);

            handler.broadcastLocal(BROADCAST_MSG);

            verify(good).sendMessage(any(TextMessage.class));
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private WebSocketSession openSession(String id) {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getId()).thenReturn(id);
        when(s.isOpen()).thenReturn(true);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("orgId", TEST_ORG);
        when(s.getAttributes()).thenReturn(attrs);
        return s;
    }

    private WebSocketSession closedSession(String id) {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getId()).thenReturn(id);
        when(s.isOpen()).thenReturn(false);
        return s;
    }
}
