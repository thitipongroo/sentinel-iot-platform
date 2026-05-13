package com.sentinel.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.dto.AuthRequest;
import com.sentinel.iot.dto.DeviceRequest;
import com.sentinel.iot.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityIntegrationTest extends BaseIntegrationTest {

    private static final UUID TEST_ORG = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void setTenantContext() {
        TenantContext.set(TEST_ORG);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ── Authentication ────────────────────────────────────────────────────────

    @SuppressWarnings("null")
    @Test
    void login_producesJwtWithCorrectRole() throws Exception {
        AuthRequest req = new AuthRequest();
        req.setUsername("admin");
        req.setPassword("admin123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void protectedEndpoint_withValidToken_isAccessible() throws Exception {
        String token = loginAndGetToken("admin", "admin123");

        mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_withExpiredOrTamperedToken_returns403() throws Exception {
        String tampered = loginAndGetToken("admin", "admin123") + "tampered";

        mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + tampered))
                .andExpect(status().isForbidden());
    }

    // ── Role-based access ─────────────────────────────────────────────────────

    @SuppressWarnings("null")
    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCreateDevice() throws Exception {
        DeviceRequest req = new DeviceRequest();
        req.setName("sec-test-device-" + System.nanoTime());

        mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @SuppressWarnings("null")
    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCannotCreateDevice() throws Exception {
        DeviceRequest req = new DeviceRequest();
        req.setName("blocked-device");

        mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCanReadDevices() throws Exception {
        mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCanReadAlerts() throws Exception {
        mockMvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isOk());
    }

    // ── Public endpoints ──────────────────────────────────────────────────────

    @Test
    void swaggerUi_isPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void prometheusEndpoint_isPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }

    @Test
    void requestId_headerIsEchoedInResponse() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("X-Request-ID", "test-trace-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "test-trace-123"));
    }

    @Test
    void requestWithoutRequestId_responseStillContainsGeneratedId() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"));
    }

    // ── helper ────────────────────────────────────────────────────────────────

    @SuppressWarnings("null")
    private String loginAndGetToken(String username, String password) throws Exception {
        AuthRequest req = new AuthRequest();
        req.setUsername(username);
        req.setPassword(password);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}
