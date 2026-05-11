package com.sentinel.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.dto.AuthRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerIntegrationTest extends BaseIntegrationTest {

    private static final String REFRESH_COOKIE = "sentinel_refresh_token";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void login_withValidCredentials_returnsAccessAndRefreshTokens() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest("admin", "admin123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();

        // Refresh token is delivered as an HttpOnly cookie, not in the response body
        assertThat(result.getResponse().getCookie(REFRESH_COOKIE)).isNotNull();
        assertThat(result.getResponse().getCookie(REFRESH_COOKIE).getValue()).isNotBlank();
    }

    @Test
    void login_withInvalidCredentials_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest("admin", "wrongpassword"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshToken_withValidToken_returnsNewTokens() throws Exception {
        // Step 1: login to get the refresh cookie
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest("operator", "op123"))))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(refreshCookie).isNotNull();

        // Step 2: use the cookie to refresh
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    private AuthRequest authRequest(String username, String password) {
        AuthRequest req = new AuthRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    @Test
    void refreshToken_withInvalidToken_returns400() throws Exception {
        // Send an invalid value in the refresh cookie — controller calls rotateRefreshToken
        // which throws IllegalArgumentException → handled as 400 by GlobalExceptionHandler
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE, "not-a-valid-token")))
                .andExpect(status().isBadRequest());
    }
}
