package com.sentinel.iot;

import com.sentinel.iot.repository.AppUserRepository;
import com.sentinel.iot.service.JwtService;
import com.sentinel.iot.websocket.JwtWebSocketHandshakeInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * หมวดที่ 8 — WebSocket Security (3 tests)
 *
 * ทดสอบ JwtWebSocketHandshakeInterceptor โดยตรง:
 *   8.1 Handshake โดยไม่มี token query param ถูกปฏิเสธ
 *   8.2 Handshake ด้วย token ที่ invalid ถูกปฏิเสธ
 *   8.3 Handshake ด้วย valid token ผ่าน และ orgId ถูก store ใน session attributes
 */
class WebSocketSecurityTest extends BaseIntegrationTest {

    @Autowired JwtWebSocketHandshakeInterceptor interceptor;
    @Autowired JwtService jwtService;
    @Autowired AppUserRepository userRepository;

    // ── 8.1 WebSocket handshake without token is rejected ────────────────────

    @Test
    void handshake_withNoToken_isRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // No 'token' query parameter set
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                null,   // wsHandler is not used by the interceptor
                attributes);

        assertThat(accepted).isFalse();
    }

    // ── 8.2 WebSocket handshake with invalid/expired token is rejected ─────────

    @Test
    void handshake_withInvalidToken_isRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("token", "completely-invalid-jwt-token-value");
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                null,
                attributes);

        assertThat(accepted).isFalse();
    }

    // ── 8.3 Valid token → handshake accepted and orgId stored in attributes ───

    @Test
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

        assertThat(accepted).isTrue();
        assertThat(attributes).containsKey("orgId");
        assertThat(attributes.get("orgId")).isEqualTo(orgId);
    }
}
