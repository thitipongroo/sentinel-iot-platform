package com.sentinel.iot.regression;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.BaseIntegrationTest;
import com.sentinel.iot.dto.AuthRequest;
import com.sentinel.iot.dto.DeviceEnrollRequest;
import com.sentinel.iot.model.Alert;
import com.sentinel.iot.repository.AlertRepository;
import com.sentinel.iot.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 3.4 RBAC Rules Regression (10 tests)
 */
class RbacRegressionTest extends BaseIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;
    @Autowired AlertRepository alertRepository;

    // 3.4.1 — OPERATOR cannot create a device
    @SuppressWarnings("null")
    @Test
    void createDevice_operator_returns403() throws Exception {
        String token = loginAndGetToken("operator", "op123");

        mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "rbac-op-" + System.nanoTime()))))
                .andExpect(status().isForbidden());
    }

    // 3.4.2 — ADMIN can create a device
    @SuppressWarnings("null")
    @Test
    void createDevice_admin_returns201() throws Exception {
        String token = loginAndGetToken("admin", "admin123");

        mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "rbac-admin-" + System.nanoTime()))))
                .andExpect(status().isCreated());
    }

    // 3.4.3 — OPERATOR cannot PATCH lifecycle
    @SuppressWarnings("null")
    @Test
    void patchLifecycle_operator_returns403() throws Exception {
        String adminToken    = loginAndGetToken("admin", "admin123");
        String operatorToken = loginAndGetToken("operator", "op123");
        String deviceId      = createDevice(adminToken, "lifecycle-rbac-" + System.nanoTime());

        mockMvc.perform(patch("/api/v1/devices/" + deviceId + "/lifecycle")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("lifecycleStatus", "ACTIVE"))))
                .andExpect(status().isForbidden());
    }

    // 3.4.4 — OPERATOR cannot PATCH firmware
    @SuppressWarnings("null")
    @Test
    void patchFirmware_operator_returns403() throws Exception {
        String adminToken    = loginAndGetToken("admin", "admin123");
        String operatorToken = loginAndGetToken("operator", "op123");
        String deviceId      = createDevice(adminToken, "firmware-rbac-" + System.nanoTime());

        mockMvc.perform(patch("/api/v1/devices/" + deviceId + "/firmware")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("firmwareVersion", "1.2.3"))))
                .andExpect(status().isForbidden());
    }

    // 3.4.5 — OPERATOR can read devices
    @Test
    void readDevices_operator_returns200() throws Exception {
        String token = loginAndGetToken("operator", "op123");

        mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // 3.4.6 — OPERATOR can read alerts
    @Test
    void readAlerts_operator_returns200() throws Exception {
        String token = loginAndGetToken("operator", "op123");

        mockMvc.perform(get("/api/v1/alerts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // 3.4.7 — OPERATOR cannot acknowledge an alert
    @Test
    void acknowledgeAlert_operator_returns403() throws Exception {
        String adminToken    = loginAndGetToken("admin", "admin123");
        String operatorToken = loginAndGetToken("operator", "op123");

        UUID alertId = createAlert(adminToken);

        mockMvc.perform(put("/api/v1/alerts/" + alertId + "/acknowledge")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());
    }

    // 3.4.8 — ADMIN can acknowledge an alert
    @Test
    void acknowledgeAlert_admin_returns204() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin123");
        UUID alertId = createAlert(adminToken);

        mockMvc.perform(put("/api/v1/alerts/" + alertId + "/acknowledge")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    // 3.4.9 — OPERATOR cannot generate an enrollment token
    @Test
    void generateEnrollmentToken_operator_returns403() throws Exception {
        String adminToken    = loginAndGetToken("admin", "admin123");
        String operatorToken = loginAndGetToken("operator", "op123");
        String deviceId      = createDevice(adminToken, "enroll-rbac-" + System.nanoTime());

        mockMvc.perform(post("/api/v1/devices/" + deviceId + "/enrollment-token")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());
    }

    // 3.4.10 — Enroll endpoint (no auth) with missing/invalid token → 400
    @SuppressWarnings("null")
    @Test
    void enrollEndpoint_withInvalidToken_returns400() throws Exception {
        DeviceEnrollRequest req = new DeviceEnrollRequest(UUID.randomUUID(), "not-a-real-token", null);

        mockMvc.perform(post("/api/v1/devices/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

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

    @SuppressWarnings("null")
    private String createDevice(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    private UUID createAlert(String adminToken) throws Exception {
        // Extract orgId from token to create alert scoped to the same tenant
        UUID orgId = jwtService.extractOrgId(adminToken);
        String deviceId = createDevice(adminToken, "alert-device-" + System.nanoTime());
        Alert alert = new Alert(UUID.fromString(deviceId), "WARNING", "rbac-test alert", orgId);
        return alertRepository.save(alert).getId();
    }
}
