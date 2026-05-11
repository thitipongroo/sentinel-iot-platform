package com.sentinel.iot;

import com.sentinel.iot.websocket.TelemetryWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link TelemetryWebSocketHandler} handles concurrent session
 * registration, deregistration, and broadcast without race conditions or NPEs.
 *
 * <p>The handler uses {@code CopyOnWriteArrayList} internally, which is
 * thread-safe for iteration during broadcast but requires testing the
 * concurrent-modification pattern (register + broadcast + unregister simultaneously).
 */
class WebSocketConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private TelemetryWebSocketHandler handler;

    @Test
    void concurrentSessionsAndBroadcast_noExceptions() throws InterruptedException {
        int sessionCount  = 50;
        int broadcastCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(sessionCount + broadcastCount);
        AtomicInteger errors = new AtomicInteger(0);

        List<WebSocketSession> sessions = new ArrayList<>();

        // Register sessions concurrently
        for (int i = 0; i < sessionCount; i++) {
            WebSocketSession session = mock(WebSocketSession.class);
            when(session.isOpen()).thenReturn(true);
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

        // Broadcast concurrently while sessions are being added
        for (int i = 0; i < broadcastCount; i++) {
            final String payload = "{\"deviceId\":\"sensor-test\",\"temperature\":" + (60 + i) + "}";
            executor.submit(() -> {
                try {
                    handler.broadcastLocal(payload);
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
                .withFailMessage("Expected 0 concurrency errors but got %d", errors.get())
                .isZero();
    }

    @Test
    void closedSessions_removedDuringBroadcast() throws Exception {
        WebSocketSession open   = mock(WebSocketSession.class);
        WebSocketSession closed = mock(WebSocketSession.class);

        when(open.isOpen()).thenReturn(true);
        when(closed.isOpen()).thenReturn(false);

        handler.afterConnectionEstablished(open);
        handler.afterConnectionEstablished(closed);

        // Broadcast should only send to open session and silently skip the closed one
        handler.broadcastLocal("{\"deviceId\":\"sensor-1\",\"temperature\":72.0}");

        verify(open, times(1)).sendMessage(any(TextMessage.class));
        verify(closed, never()).sendMessage(any());
    }

    @Test
    void disconnectedSession_notBroadcastedAfterClose() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);
        handler.broadcastLocal("{\"t\":1}");
        verify(session, times(1)).sendMessage(any(TextMessage.class));

        // Simulate disconnect
        handler.afterConnectionClosed(session, null);
        handler.broadcastLocal("{\"t\":2}");

        // Still only 1 broadcast — session was removed on close
        verify(session, times(1)).sendMessage(any(TextMessage.class));
    }
}
