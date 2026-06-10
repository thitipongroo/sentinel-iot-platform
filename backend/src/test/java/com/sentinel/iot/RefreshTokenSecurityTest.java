package com.sentinel.iot;

import com.sentinel.iot.model.RefreshToken;
import com.sentinel.iot.repository.RefreshTokenRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

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
@DisplayName("Refresh Token Security — session management")
class RefreshTokenSecurityTest extends BaseIntegrationTest {

    private static final String REFRESH_COOKIE = "sentinel_refresh_token";

    @Autowired RefreshTokenRepository refreshTokenRepository;

    // ── Token validation ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Token validation")
    class TokenValidation {

        @Test
        @DisplayName("a random string used as a refresh token is rejected with HTTP 400")
        void randomStringRefreshToken_isRejected() throws Exception {
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie(REFRESH_COOKIE, "totally-random-fake-token-value")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("an already-rotated refresh token is rejected on the second use")
        void alreadyUsedRefreshToken_isRejected() throws Exception {
            Cookie refreshCookie = loginAndGetRefreshCookie("operator", "op123");

            // First refresh — rotates token (original token is now revoked)
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(refreshCookie))
                    .andExpect(status().isOk());

            // Second use of the same original token → 400
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(refreshCookie))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("an expired refresh token (past expiresAt) is rejected with HTTP 400")
        void expiredRefreshToken_isRejected() throws Exception {
            String rawToken = "expired-raw-" + UUID.randomUUID();
            String tokenHash = sha256(rawToken);
            RefreshToken expired = new RefreshToken(tokenHash, "admin", Instant.now().minusSeconds(86_400));
            refreshTokenRepository.save(expired);

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie(REFRESH_COOKIE, rawToken)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── Single-use and family revocation ─────────────────────────────────────

    @Nested
    @DisplayName("Single-use enforcement and family revocation")
    class SingleUseEnforcement {

        @Test
        @DisplayName("replaying a rotated token triggers family revocation (RFC 6819) — all sessions blocked")
        void tokenReuseDetection_revokesAllSessionsForUser() throws Exception {
            Cookie session1Cookie = loginAndGetRefreshCookie("admin", "admin123");
            Cookie session2Cookie = loginAndGetRefreshCookie("admin", "admin123");

            // Legitimately rotate session-1
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(session1Cookie))
                    .andExpect(status().isOk());

            // Replay session-1's original token → reuse detected → family revocation
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(session1Cookie))
                    .andExpect(status().isBadRequest());

            // session-2 must also be revoked (entire family invalidated per RFC 6819)
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(session2Cookie))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── Cookie security attributes ────────────────────────────────────────────

    @Nested
    @DisplayName("Cookie security attributes")
    class CookieAttributes {

        @SuppressWarnings("null")
        @Test
        @DisplayName("login response body does not expose the refresh token — only the HttpOnly cookie contains it")
        void refreshTokenNotExposedInResponseBody() throws Exception {
            var result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(authRequest("admin", "admin123"))))
                    .andExpect(status().isOk())
                    .andReturn();

            var json = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(json.has("refreshToken"))
                    .as("refreshToken must not appear in the response body").isFalse();
            assertThat(result.getResponse().getCookie(REFRESH_COOKIE))
                    .as("refresh cookie must be present").isNotNull();
            assertThat(result.getResponse().getCookie(REFRESH_COOKIE).getValue())
                    .as("refresh cookie must be non-blank").isNotBlank();
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("refresh cookie carries HttpOnly, Secure, and SameSite=Strict attributes")
        void refreshCookieHasSecurityAttributes() throws Exception {
            var result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(authRequest("admin", "admin123"))))
                    .andExpect(status().isOk())
                    .andReturn();

            String setCookie = result.getResponse().getHeader("Set-Cookie");
            assertThat(setCookie).as("Set-Cookie header").isNotNull();
            assertThat(setCookie).as("must be HttpOnly").containsIgnoringCase("HttpOnly");
            assertThat(setCookie).as("must be Secure").containsIgnoringCase("Secure");
            assertThat(setCookie).as("must be SameSite=Strict").containsIgnoringCase("SameSite=Strict");
        }
    }

    // ── Session revocation ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Session revocation on logout")
    class SessionRevocation {

        @Test
        @DisplayName("logout revokes all refresh tokens for the user across all devices")
        void logout_revokesAllRefreshTokensForUser() throws Exception {
            Cookie device1Cookie = loginAndGetRefreshCookie("operator", "op123");
            Cookie device2Cookie = loginAndGetRefreshCookie("operator", "op123");
            String accessToken   = loginAndGetToken("operator", "op123");

            // Logout from device 1 — calls revokeAllRefreshTokens(username)
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNoContent());

            mockMvc.perform(post("/api/v1/auth/refresh").cookie(device1Cookie))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post("/api/v1/auth/refresh").cookie(device2Cookie))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("null")
    private Cookie loginAndGetRefreshCookie(String username, String password) throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(cookie).isNotNull();
        return cookie;
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
