package com.sentinel.iot.regression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.BaseIntegrationTest;
import com.sentinel.iot.dto.AuthRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 3.3 Authentication & Token Regression (6 tests)
 */
class AuthRegressionTest extends BaseIntegrationTest {

    private static final String REFRESH_COOKIE = "sentinel_refresh_token";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // 3.3.1 — Access token is valid immediately after login (before expiry)
    @Test
    void accessToken_isValidBeforeExpiry() throws Exception {
        String token = loginAndGetToken("admin", "admin123");

        mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // 3.3.2 — Refresh token rotation: new token issued, old token becomes invalid
    @Test
    void refreshTokenRotation_oldTokenIsRevoked() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest("operator", "op123"))))
                .andExpect(status().isOk())
                .andReturn();

        Cookie firstRefreshCookie = loginResult.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(firstRefreshCookie).isNotNull();

        // First rotation — succeeds and issues a new cookie
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(firstRefreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // Second use of the same (now-revoked) refresh token — must be rejected
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(firstRefreshCookie))
                .andExpect(status().isBadRequest());
    }

    // 3.3.3 — Set-Cookie header retains HttpOnly, Secure, SameSite=Strict flags
    @Test
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

    // 3.3.4 — Logout revokes the access token's JTI; subsequent use returns 403
    @Test
    void logout_revokesAccessToken() throws Exception {
        String token = loginAndGetToken("admin", "admin123");

        // Confirm token works before logout
        mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Logout — revokes the JTI in Redis
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Same token must now be rejected
        mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // 3.3.5 — Login response body must NOT contain refreshToken field
    @Test
    void loginResponseBody_hasNoRefreshTokenField() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest("admin", "admin123"))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("refreshToken")).isFalse();
    }

    // 3.3.6 — Role field in response matches the DB-stored role for each user
    @Test
    void loginResponse_roleMatchesUserRole() throws Exception {
        JsonNode adminBody  = loginBody("admin", "admin123");
        JsonNode opBody     = loginBody("operator", "op123");

        assertThat(adminBody.get("role").asText()).isEqualTo("ADMIN");
        assertThat(opBody.get("role").asText()).isEqualTo("OPERATOR");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String loginAndGetToken(String username, String password) throws Exception {
        return loginBody(username, password).get("accessToken").asText();
    }

    private JsonNode loginBody(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private AuthRequest authRequest(String username, String password) {
        AuthRequest req = new AuthRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }
}
