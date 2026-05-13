package com.sentinel.iot.regression;

import com.sentinel.iot.BaseIntegrationTest;
import com.sentinel.iot.service.JwtService;
import com.sentinel.iot.service.UserDetailsServiceImpl;
import com.sentinel.iot.websocket.JwtWebSocketHandshakeInterceptor;
import com.sentinel.iot.websocket.TelemetryWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 3.8 WebSocket Behavior Regression (5 tests)
 */
class WebSocketRegressionTest extends BaseIntegrationTest {

    @Autowired JwtWebSocketHandshakeInterceptor interceptor;
    @Autowired TelemetryWebSocketHandler handler;
    @Autowired JwtService jwtService;
    @Autowired UserDetailsServiceImpl userDetailsService;

    // 3.8.1 — Valid token → beforeHandshake returns true and sets orgId attribute
    @SuppressWarnings("null")
    @Test
    void validToken_handshakeAccepted_orgIdSetInAttributes() throws Exception {
        String token = jwtService.generateAccessToken("admin", "ADMIN", UUID.randomUUID());

        MockHttpServletRequest servletReq = new MockHttpServletRequest();
        servletReq.setParameter("token", token);
        ServletServerHttpRequest  request  = new ServletServerHttpRequest(servletReq);
        ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());

        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = interceptor.beforeHandshake(request, response, handler, attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes).containsKey("orgId");
        assertThat(attributes.get("orgId")).isInstanceOf(UUID.class);
    }

    // 3.8.2 — Missing token query parameter → beforeHandshake returns false
    @Test
    void missingToken_handshakeRejected() throws Exception {
        MockHttpServletRequest servletReq = new MockHttpServletRequest();
        // no "token" parameter
        ServletServerHttpRequest  request  = new ServletServerHttpRequest(servletReq);
        ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());

        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = interceptor.beforeHandshake(request, response, handler, attributes);

        assertThat(accepted).isFalse();
    }

    // 3.8.3 — broadcastLocal correctly parses "orgId|payload" and delivers only payload to session
    @Test
    void broadcastLocal_parsesOrgIdPipePayloadFormat() throws Exception {
        UUID orgId = UUID.randomUUID();
        String payload = "{\"deviceId\":\"d1\",\"temperature\":23.5}";

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(Map.of("orgId", orgId));

        handler.afterConnectionEstablished(session);
        handler.broadcastLocal(orgId + "|" + payload);

        verify(session, times(1)).sendMessage(new TextMessage(payload));
    }

    // 3.8.4 — broadcastLocal does not deliver messages to sessions of a different org
    @SuppressWarnings("null")
    @Test
    void broadcastLocal_doesNotDeliverCrossOrgMessages() throws Exception {
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();

        WebSocketSession sessionA = mock(WebSocketSession.class);
        when(sessionA.isOpen()).thenReturn(true);
        when(sessionA.getAttributes()).thenReturn(Map.of("orgId", orgA));

        WebSocketSession sessionB = mock(WebSocketSession.class);
        when(sessionB.isOpen()).thenReturn(true);
        when(sessionB.getAttributes()).thenReturn(Map.of("orgId", orgB));

        handler.afterConnectionEstablished(sessionA);
        handler.afterConnectionEstablished(sessionB);

        // Broadcast only to org A
        handler.broadcastLocal(orgA + "|{\"t\":1}");

        verify(sessionA, times(1)).sendMessage(any(TextMessage.class));
        verify(sessionB, never()).sendMessage(any(TextMessage.class));
    }

    // 3.8.5 — Closed session is removed from the session set after afterConnectionClosed
    @SuppressWarnings("null")
    @Test
    void closedSession_isRemovedFromSessionSet_noBroadcastAfterClose() throws Exception {
        UUID orgId = UUID.randomUUID();
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(Map.of("orgId", orgId));

        handler.afterConnectionEstablished(session);
        handler.broadcastLocal(orgId + "|{\"t\":1}");
        verify(session, times(1)).sendMessage(any(TextMessage.class));

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        handler.broadcastLocal(orgId + "|{\"t\":2}");

        // Still only 1 send — session was removed on close
        verify(session, times(1)).sendMessage(any(TextMessage.class));
    }
}
