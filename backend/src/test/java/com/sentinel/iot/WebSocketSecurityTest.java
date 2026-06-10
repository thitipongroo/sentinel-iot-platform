package com.sentinel.iot;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sentinel.iot.repository.AppUserRepository;
import com.sentinel.iot.service.JwtService;
import com.sentinel.iot.websocket.JwtWebSocketHandshakeInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * หมวดที่ 8 — WebSocket Security (4 tests)
 *
 * ทดสอบ JwtWebSocketHandshakeInterceptor โดยตรง:
 *   8.1 Handshake โดยไม่มี token query param ถูกปฏิเสธ
 *   8.2 Handshake ด้วย token ที่ invalid ถูกปฏิเสธ
 *   8.3 Handshake ด้วย valid token ผ่าน และ orgId ถูก store ใน session attributes
 *   8.4 Token value does not appear in application log output during handshake
 */
@DisplayName("WebSocket Security — handshake interceptor")
class WebSocketSecurityTest extends BaseIntegrationTest {

    @Autowired JwtWebSocketHandshakeInterceptor interceptor;
    @Autowired JwtService                        jwtService;
    @Autowired AppUserRepository                 userRepository;

    // ── Handshake rejection ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Handshake rejection")
    class HandshakeRejection {

        @Test
        @DisplayName("handshake without a 'token' query parameter is rejected")
        void handshake_withNoToken_isRejected() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            Map<String, Object> attributes = new HashMap<>();

            boolean accepted = interceptor.beforeHandshake(
                    new ServletServerHttpRequest(request),
                    new ServletServerHttpResponse(new MockHttpServletResponse()),
                    null,
                    attributes);

            assertThat(accepted).as("handshake without token must be rejected").isFalse();
        }

        @Test
        @DisplayName("handshake with an invalid/malformed token is rejected")
        void handshake_withInvalidToken_isRejected() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter("token", "completely-invalid-jwt-token-value");
            Map<String, Object> attributes = new HashMap<>();

            boolean accepted = interceptor.beforeHandshake(
                    new ServletServerHttpRequest(request),
                    new ServletServerHttpResponse(new MockHttpServletResponse()),
                    null,
                    attributes);

            assertThat(accepted).as("handshake with invalid token must be rejected").isFalse();
        }
    }

    // ── Successful handshake ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Successful handshake")
    class SuccessfulHandshake {

        @SuppressWarnings("null")
        @Test
        @DisplayName("valid token is accepted and the orgId claim is stored in session attributes")
        void handshake_withValidToken_storesOrgIdInAttributes() throws Exception {
            UUID orgId = userRepository.findByUsername("admin")
                    .orElseThrow(() -> new IllegalStateException("admin user not seeded"))
                    .getOrganizationId();
            String token = jwtService.generateAccessToken("admin", "ADMIN", orgId);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter("token", token);
            Map<String, Object> attributes = new HashMap<>();

            boolean accepted = interceptor.beforeHandshake(
                    new ServletServerHttpRequest(request),
                    new ServletServerHttpResponse(new MockHttpServletResponse()),
                    null,
                    attributes);

            assertThat(accepted).as("handshake with valid token must be accepted").isTrue();
            assertThat(attributes).as("orgId must be stored in session attributes").containsKey("orgId");
            assertThat(attributes.get("orgId")).as("orgId value must match the token claim").isEqualTo(orgId);
        }
    }

    // ── Token confidentiality ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Token confidentiality in logs")
    class TokenConfidentiality {

        @SuppressWarnings("null")
        @Test
        @DisplayName("the raw JWT token value must not appear in application logs during handshake")
        void handshake_doesNotLogRawJwtTokenValue() throws Exception {
            UUID orgId = userRepository.findByUsername("admin")
                    .orElseThrow(() -> new IllegalStateException("admin user not seeded"))
                    .getOrganizationId();
            String token = jwtService.generateAccessToken("admin", "ADMIN", orgId);

            Logger interceptorLogger = (Logger) LoggerFactory.getLogger(JwtWebSocketHandshakeInterceptor.class);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            interceptorLogger.addAppender(appender);

            try {
                MockHttpServletRequest request = new MockHttpServletRequest();
                request.setParameter("token", token);
                interceptor.beforeHandshake(
                        new ServletServerHttpRequest(request),
                        new ServletServerHttpResponse(new MockHttpServletResponse()),
                        null,
                        new HashMap<>());
            } finally {
                interceptorLogger.detachAppender(appender);
            }

            List<ILoggingEvent> logs = appender.list;
            logs.forEach(event ->
                    assertThat(event.getFormattedMessage())
                            .as("log message must not contain the raw JWT token value")
                            .doesNotContain(token));
        }
    }
}
