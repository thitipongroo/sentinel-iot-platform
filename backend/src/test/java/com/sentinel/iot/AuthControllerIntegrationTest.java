package com.sentinel.iot;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("AuthController — login and token refresh")
class AuthControllerIntegrationTest extends BaseIntegrationTest {

    private static final String REFRESH_COOKIE = "sentinel_refresh_token";

    // ── Login ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Login")
    class Login {

        @SuppressWarnings("null")
        @Test
        @DisplayName("valid credentials return an access token, role, and an HttpOnly refresh cookie")
        void login_withValidCredentials_returnsAccessAndRefreshTokens() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(authRequest("admin", "admin123"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.role").value("ADMIN"))
                    .andReturn();

            // Refresh token is delivered as an HttpOnly cookie, not in the response body
            assertThat(result.getResponse().getCookie(REFRESH_COOKIE))
                    .as("refresh cookie must be present").isNotNull();
            assertThat(result.getResponse().getCookie(REFRESH_COOKIE).getValue())
                    .as("refresh cookie must be non-blank").isNotBlank();
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("invalid password returns HTTP 401")
        void login_withInvalidCredentials_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(authRequest("admin", "wrongpassword"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── Refresh token ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Refresh token")
    class RefreshToken {

        @SuppressWarnings("null")
        @Test
        @DisplayName("valid refresh cookie returns a new access token")
        void refreshToken_withValidToken_returnsNewTokens() throws Exception {
            MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(authRequest("operator", "op123"))))
                    .andExpect(status().isOk())
                    .andReturn();

            Cookie refreshCookie = loginResult.getResponse().getCookie(REFRESH_COOKIE);
            assertThat(refreshCookie).as("refresh cookie from login").isNotNull();

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(refreshCookie))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty());
        }

        @Test
        @DisplayName("invalid refresh cookie value returns HTTP 400")
        void refreshToken_withInvalidToken_returns400() throws Exception {
            // GlobalExceptionHandler maps IllegalArgumentException from rotateRefreshToken to 400
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie(REFRESH_COOKIE, "not-a-valid-token")))
                    .andExpect(status().isBadRequest());
        }
    }
}
