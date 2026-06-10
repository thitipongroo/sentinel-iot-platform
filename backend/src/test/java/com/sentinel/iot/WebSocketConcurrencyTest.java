package com.sentinel.iot;

import com.sentinel.iot.websocket.TelemetryWebSocketHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link TelemetryWebSocketHandler} handles concurrent session
 * registration, deregistration, and broadcast without race conditions or NPEs.
 */
@DisplayName("WebSocketHandler — concurrency and session lifecycle")
class WebSocketConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private TelemetryWebSocketHandler handler;

    // ── Concurrency invariants ────────────────────────────────────────────────

    @Nested
    @DisplayName("Concurrency invariants")
    class ConcurrencyInvariants {

        @Test
        @DisplayName("50 concurrent session registrations interleaved with 20 broadcasts produce zero errors")
        void concurrentSessionsAndBroadcast_noExceptions() throws InterruptedException {
            String orgId       = UUID.randomUUID().toString();
            int sessionCount   = 50;
            int broadcastCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch  latch    = new CountDownLatch(sessionCount + broadcastCount);
            AtomicInteger   errors   = new AtomicInteger(0);

            List<WebSocketSession> sessions = new ArrayList<>();

            for (int i = 0; i < sessionCount; i++) {
                WebSocketSession session = mock(WebSocketSession.class);
                when(session.isOpen()).thenReturn(true);
                when(session.getAttributes()).thenReturn(Map.of("orgId", UUID.fromString(orgId)));
                sessions.add(session);

                executor.submit(() -> {
                    try {
                        handler.afterConnectionEstablished(session);
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            for (int i = 0; i < broadcastCount; i++) {
                final String msg = orgId + "|{\"deviceId\":\"sensor-test\",\"temperature\":" + (60 + i) + "}";
                executor.submit(() -> {
                    try {
                        handler.broadcastLocal(msg);
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdownNow();

            assertThat(errors.get())
                    .as("concurrent session registration + broadcast must produce 0 errors")
                    .isZero();
        }
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Session lifecycle")
    class SessionLifecycle {

        @SuppressWarnings("null")
        @Test
        @DisplayName("closed sessions are skipped during broadcast — open sessions still receive messages")
        void closedSessions_removedDuringBroadcast() throws Exception {
            String orgId = UUID.randomUUID().toString();
            WebSocketSession open   = mock(WebSocketSession.class);
            WebSocketSession closed = mock(WebSocketSession.class);

            when(open.isOpen()).thenReturn(true);
            when(closed.isOpen()).thenReturn(false);
            when(open.getAttributes()).thenReturn(Map.of("orgId", UUID.fromString(orgId)));
            when(closed.getAttributes()).thenReturn(Map.of("orgId", UUID.fromString(orgId)));

            handler.afterConnectionEstablished(open);
            handler.afterConnectionEstablished(closed);

            handler.broadcastLocal(orgId + "|{\"deviceId\":\"sensor-1\",\"temperature\":72.0}");

            verify(open, times(1)).sendMessage(any(TextMessage.class));
            verify(closed, never()).sendMessage(any());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("session receives no messages after afterConnectionClosed is called")
        void disconnectedSession_notBroadcastedAfterClose() throws Exception {
            String orgId = UUID.randomUUID().toString();
            WebSocketSession session = mock(WebSocketSession.class);
            when(session.isOpen()).thenReturn(true);
            when(session.getAttributes()).thenReturn(Map.of("orgId", UUID.fromString(orgId)));

            handler.afterConnectionEstablished(session);
            handler.broadcastLocal(orgId + "|{\"t\":1}");
            verify(session, times(1)).sendMessage(any(TextMessage.class));

            handler.afterConnectionClosed(session, null);
            handler.broadcastLocal(orgId + "|{\"t\":2}");

            // Still exactly 1 invocation — nothing sent after close
            verify(session, times(1)).sendMessage(any(TextMessage.class));
        }
    }
}
