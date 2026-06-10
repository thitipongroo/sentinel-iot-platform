package com.sentinel.iot.regression;

import com.sentinel.iot.BaseIntegrationTest;
import com.sentinel.iot.service.JwtService;
import com.sentinel.iot.service.UserDetailsServiceImpl;
import com.sentinel.iot.websocket.JwtWebSocketHandshakeInterceptor;
import com.sentinel.iot.websocket.TelemetryWebSocketHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
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
@DisplayName("WebSocketRegressionTest — WebSocket behavior invariants")
class WebSocketRegressionTest extends BaseIntegrationTest {

    @Autowired JwtWebSocketHandshakeInterceptor interceptor;
    @Autowired TelemetryWebSocketHandler        handler;
    @Autowired JwtService                       jwtService;
    @Autowired UserDetailsServiceImpl           userDetailsService;

    // ── Handshake security ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Handshake security")
    class HandshakeSecurity {

        @SuppressWarnings("null")
        @Test
        @DisplayName("valid token in query parameter → handshake accepted and orgId set in attributes")
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

        @Test
        @DisplayName("missing token query parameter → handshake rejected")
        void missingToken_handshakeRejected() throws Exception {
            MockHttpServletRequest servletReq = new MockHttpServletRequest();
            ServletServerHttpRequest  request  = new ServletServerHttpRequest(servletReq);
            ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());

            Map<String, Object> attributes = new HashMap<>();
            boolean accepted = interceptor.beforeHandshake(request, response, handler, attributes);

            assertThat(accepted).isFalse();
        }
    }

    // ── Broadcast routing ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Broadcast routing")
    class BroadcastRouting {

        @Test
        @DisplayName("broadcastLocal delivers only the payload (not the orgId prefix) to the matching session")
        void broadcastLocal_parsesOrgIdPipePayloadFormat() throws Exception {
            UUID orgId   = UUID.randomUUID();
            String payload = "{\"deviceId\":\"d1\",\"temperature\":23.5}";

            WebSocketSession session = mock(WebSocketSession.class);
            when(session.isOpen()).thenReturn(true);
            when(session.getAttributes()).thenReturn(Map.of("orgId", orgId));

            handler.afterConnectionEstablished(session);
            handler.broadcastLocal(orgId + "|" + payload);

            verify(session, times(1)).sendMessage(new TextMessage(payload));
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("broadcastLocal does not deliver org-A messages to an org-B session")
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

            handler.broadcastLocal(orgA + "|{\"t\":1}");

            verify(sessionA, times(1)).sendMessage(any(TextMessage.class));
            verify(sessionB, never()).sendMessage(any(TextMessage.class));
        }
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Session lifecycle")
    class SessionLifecycle {

        @SuppressWarnings("null")
        @Test
        @DisplayName("session removed after afterConnectionClosed — receives no further broadcasts")
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

            verify(session, times(1)).sendMessage(any(TextMessage.class));
        }
    }
}
