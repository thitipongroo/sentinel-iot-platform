package com.sentinel.iot.regression;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinel.iot.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 3.3 Authentication & Token Regression (6 tests)
 */
@DisplayName("AuthRegressionTest — authentication and token lifecycle")
class AuthRegressionTest extends BaseIntegrationTest {

    private static final String REFRESH_COOKIE = "sentinel_refresh_token";

    // ── Token validity ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Token validity")
    class TokenValidity {

        @Test
        @DisplayName("freshly-issued access token is accepted by protected endpoints")
        void accessToken_isValidBeforeExpiry() throws Exception {
            String token = loginAndGetToken("admin", "admin123");

            mockMvc.perform(get("/api/v1/devices")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    // ── Token rotation ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Token rotation")
    class TokenRotation {

        @Test
        @DisplayName("after first rotation, re-using the original refresh token is rejected")
        void refreshTokenRotation_oldTokenIsRevoked() throws Exception {
            @SuppressWarnings("null")
            MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(authRequest("operator", "op123"))))
                    .andExpect(status().isOk())
                    .andReturn();

            Cookie firstRefreshCookie = loginResult.getResponse().getCookie(REFRESH_COOKIE);
            assertThat(firstRefreshCookie).isNotNull();

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(firstRefreshCookie))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty());

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(firstRefreshCookie))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── Cookie security ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cookie security flags")
    class CookieSecurity {

        @SuppressWarnings("null")
        @Test
        @DisplayName("Set-Cookie header carries HttpOnly and SameSite=Strict flags")
        void loginResponse_cookieFlagsUnchanged() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(authRequest("admin", "admin123"))))
                    .andExpect(status().isOk())
                    .andReturn();

            String setCookie = result.getResponse().getHeader("Set-Cookie");
            assertThat(setCookie).isNotNull();
            assertThat(setCookie).containsIgnoringCase("HttpOnly");
            assertThat(setCookie).containsIgnoringCase("SameSite=Strict");
        }
    }

    // ── Session revocation ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Session revocation")
    class SessionRevocation {

        @Test
        @DisplayName("logout revokes the JTI — subsequent requests with that token return 403")
        void logout_revokesAccessToken() throws Exception {
            String token = loginAndGetToken("admin", "admin123");

            mockMvc.perform(get("/api/v1/devices")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/devices")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    // ── Response contract ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Login response contract")
    class ResponseContract {

        @SuppressWarnings("null")
        @Test
        @DisplayName("login response body never contains a refreshToken field")
        void loginResponseBody_hasNoRefreshTokenField() throws Exception {
            JsonNode body = loginBody("admin", "admin123");
            assertThat(body.has("refreshToken")).isFalse();
        }

        @Test
        @DisplayName("role field in login response matches the DB-stored role for each user")
        void loginResponse_roleMatchesUserRole() throws Exception {
            JsonNode adminBody = loginBody("admin", "admin123");
            JsonNode opBody    = loginBody("operator", "op123");

            assertThat(adminBody.get("role").asText()).isEqualTo("ADMIN");
            assertThat(opBody.get("role").asText()).isEqualTo("OPERATOR");
        }
    }

    // ── private helper (returns full response body, unique to this file) ──────

    @SuppressWarnings("null")
    private JsonNode loginBody(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
