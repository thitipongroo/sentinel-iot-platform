package com.sentinel.iot;

import com.sentinel.iot.websocket.TelemetryWebSocketHandler;
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
class WebSocketConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private TelemetryWebSocketHandler handler;

    @Test
    void concurrentSessionsAndBroadcast_noExceptions() throws InterruptedException {
        String orgId = UUID.randomUUID().toString();
        int sessionCount  = 50;
        int broadcastCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(sessionCount + broadcastCount);
        AtomicInteger errors = new AtomicInteger(0);

        List<WebSocketSession> sessions = new ArrayList<>();

        for (int i = 0; i < sessionCount; i++) {
            WebSocketSession session = mock(WebSocketSession.class);
            when(session.isOpen()).thenReturn(true);
            when(session.getAttributes()).thenReturn(
                    Map.of("orgId", UUID.fromString(orgId)));
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
                .withFailMessage("Expected 0 concurrency errors but got %d", errors.get())
                .isZero();
    }

    @SuppressWarnings("null")
    @Test
    void closedSessions_removedDuringBroadcast() throws Exception {
        String orgId = UUID.randomUUID().toString();
        WebSocketSession open   = mock(WebSocketSession.class);
        WebSocketSession closed = mock(WebSocketSession.class);

        when(open.isOpen()).thenReturn(true);
        when(closed.isOpen()).thenReturn(false);
        when(open.getAttributes()).thenReturn(
                Map.of("orgId", UUID.fromString(orgId)));
        when(closed.getAttributes()).thenReturn(
                Map.of("orgId", UUID.fromString(orgId)));

        handler.afterConnectionEstablished(open);
        handler.afterConnectionEstablished(closed);

        handler.broadcastLocal(orgId + "|{\"deviceId\":\"sensor-1\",\"temperature\":72.0}");

        verify(open, times(1)).sendMessage(any(TextMessage.class));
        verify(closed, never()).sendMessage(any());
    }

    @SuppressWarnings("null")
    @Test
    void disconnectedSession_notBroadcastedAfterClose() throws Exception {
        String orgId = UUID.randomUUID().toString();
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(
                Map.of("orgId", UUID.fromString(orgId)));

        handler.afterConnectionEstablished(session);
        handler.broadcastLocal(orgId + "|{\"t\":1}");
        verify(session, times(1)).sendMessage(any(TextMessage.class));

        handler.afterConnectionClosed(session, null);
        handler.broadcastLocal(orgId + "|{\"t\":2}");

        verify(session, times(1)).sendMessage(any(TextMessage.class));
    }
}
