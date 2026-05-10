package com.sentinel.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.dto.AuthRequest;
import com.sentinel.iot.dto.RefreshRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void login_withValidCredentials_returnsAccessAndRefreshTokens() throws Exception {
        AuthRequest req = new AuthRequest();
        req.setUsername("admin");
        req.setPassword("admin123");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("accessToken");
    }

    @Test
    void login_withInvalidCredentials_returns401() throws Exception {
        AuthRequest req = new AuthRequest();
        req.setUsername("admin");
        req.setPassword("wrongpassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshToken_withValidToken_returnsNewTokens() throws Exception {
        AuthRequest loginReq = new AuthRequest();
        loginReq.setUsername("operator");
        loginReq.setPassword("op123");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = objectMapper.readTree(
            loginResult.getResponse().getContentAsString()).get("refreshToken").asText();

        RefreshRequest refreshReq = new RefreshRequest();
        refreshReq.setRefreshToken(refreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void refreshToken_withInvalidToken_returns400() throws Exception {
        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken("not-a-valid-token");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
