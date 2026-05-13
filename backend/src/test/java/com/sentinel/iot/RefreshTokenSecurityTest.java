package com.sentinel.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.dto.AuthRequest;
import com.sentinel.iot.model.RefreshToken;
import com.sentinel.iot.repository.RefreshTokenRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * หมวดที่ 2 — Refresh Token & Session Management Security (7 tests)
 *
 * ทดสอบ: fake token, single-use enforcement, reuse detection (family revocation),
 *        token not in response body, HttpOnly cookie attributes,
 *        logout revocation across sessions, expired token.
 */
class RefreshTokenSecurityTest extends BaseIntegrationTest {

    private static final String REFRESH_COOKIE = "sentinel_refresh_token";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    // ── 2.1 Fake refresh token is rejected ───────────────────────────────────

    @Test
    void randomStringRefreshToken_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE, "totally-random-fake-token-value")))
                .andExpect(status().isBadRequest());
    }

    // ── 2.2 Already-used (rotated) refresh token is rejected ─────────────────

    @Test
    void alreadyUsedRefreshToken_isRejected() throws Exception {
        Cookie refreshCookie = loginAndGetRefreshCookie("operator", "op123");

        // First refresh — rotates token (old token now revoked)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isOk());

        // Second refresh with the same (now revoked) original cookie → 400
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isBadRequest());
    }

    // ── 2.3 Reuse detection revokes entire token family ──────────────────────

    @Test
    void tokenReuseDetection_revokesAllSessionsForUser() throws Exception {
        // Login twice to create two independent refresh token rows for the same user
        Cookie session1Cookie = loginAndGetRefreshCookie("admin", "admin123");
        Cookie session2Cookie = loginAndGetRefreshCookie("admin", "admin123");

        // Rotate session-1 token legitimately
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(session1Cookie))
                .andExpect(status().isOk());

        // Replay session-1's original token → suspicious reuse detected
        // JwtService.rotateRefreshToken() calls revokeAllByUsername → revokes all tokens
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(session1Cookie))
                .andExpect(status().isBadRequest());

        // session-2 should also now be revoked (family revocation per RFC 6819)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(session2Cookie))
                .andExpect(status().isBadRequest());
    }

    // ── 2.4 Refresh token not exposed in login response body ─────────────────

    @SuppressWarnings("null")
@Test
    void refreshTokenNotExposedInResponseBody() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest("admin", "admin123"))))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        var json = objectMapper.readTree(body);

        // refreshToken field must be absent or explicitly null in the body
        assertThat(json.has("refreshToken")).isFalse();

        // The actual token is in the HttpOnly cookie, not the body
        assertThat(result.getResponse().getCookie(REFRESH_COOKIE)).isNotNull();
        assertThat(result.getResponse().getCookie(REFRESH_COOKIE).getValue()).isNotBlank();
    }

    // ── 2.5 Refresh cookie carries HttpOnly, Secure, SameSite=Strict ─────────

    @SuppressWarnings("null")
@Test
    void refreshCookieHasSecurityAttributes() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest("admin", "admin123"))))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).containsIgnoringCase("HttpOnly");
        assertThat(setCookie).containsIgnoringCase("Secure");
        assertThat(setCookie).containsIgnoringCase("SameSite=Strict");
    }

    // ── 2.6 Logout revokes all sessions across devices ───────────────────────

    @Test
    void logout_revokesAllRefreshTokensForUser() throws Exception {
        // Simulate two independent device sessions for the same user
        Cookie device1Cookie = loginAndGetRefreshCookie("operator", "op123");
        Cookie device2Cookie = loginAndGetRefreshCookie("operator", "op123");

        // Get an access token for device 1 to perform logout
        String accessToken = loginAndGetToken("operator", "op123");

        // Logout from device 1 — this calls revokeAllRefreshTokens(username)
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // device 1 refresh token should now fail
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(device1Cookie))
                .andExpect(status().isBadRequest());

        // device 2 refresh token is also revoked (all tokens for user were revoked)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(device2Cookie))
                .andExpect(status().isBadRequest());
    }

    // ── 2.7 Expired refresh token is rejected ────────────────────────────────

    @Test
    void expiredRefreshToken_isRejected() throws Exception {
        // Create a raw token value and insert the hash with a past expiresAt directly
        String rawToken = "expired-raw-" + UUID.randomUUID();
        String tokenHash = sha256(rawToken);
        RefreshToken expired = new RefreshToken(tokenHash, "admin", Instant.now().minusSeconds(86_400));
        refreshTokenRepository.save(expired);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE, rawToken)))
                .andExpect(status().isBadRequest());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("null")
private Cookie loginAndGetRefreshCookie(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(cookie).isNotNull();
        return cookie;
    }

    @SuppressWarnings("null")
private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private AuthRequest authRequest(String username, String password) {
        AuthRequest req = new AuthRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
