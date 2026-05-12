package com.sentinel.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.dto.AuthRequest;
import com.sentinel.iot.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * หมวดที่ 3 — Authorization / RBAC (8 tests)
 *
 * ทดสอบ: OPERATOR privilege escalation, unauthenticated access, IDOR cross-org
 */
class RbacSecurityTest extends BaseIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;

    // ── 3.1 OPERATOR cannot create device ────────────────────────────────────

    @Test
    void operator_cannotCreateDevice() throws Exception {
        String token = loginAndGetToken("operator", "op123");

        mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"blocked-device\"}"))
                .andExpect(status().isForbidden());
    }

    // ── 3.2 OPERATOR cannot patch lifecycle ──────────────────────────────────

    @Test
    void operator_cannotPatchLifecycle() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin123");
        String deviceId = createDevice(adminToken, "rbac-lifecycle-" + System.nanoTime());

        String operatorToken = loginAndGetToken("operator", "op123");

        mockMvc.perform(patch("/api/v1/devices/" + deviceId + "/lifecycle")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lifecycleStatus\":\"INACTIVE\"}"))
                .andExpect(status().isForbidden());
    }

    // ── 3.3 OPERATOR cannot patch firmware ───────────────────────────────────

    @Test
    void operator_cannotPatchFirmware() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin123");
        String deviceId = createDevice(adminToken, "rbac-firmware-" + System.nanoTime());

        String operatorToken = loginAndGetToken("operator", "op123");

        mockMvc.perform(patch("/api/v1/devices/" + deviceId + "/firmware")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmwareVersion\":\"1.0.0\"}"))
                .andExpect(status().isForbidden());
    }

    // ── 3.4 OPERATOR cannot acknowledge alert ────────────────────────────────

    @Test
    void operator_cannotAcknowledgeAlert() throws Exception {
        String operatorToken = loginAndGetToken("operator", "op123");

        // @PreAuthorize("hasRole('ADMIN')") on the endpoint — any UUID triggers the check
        mockMvc.perform(put("/api/v1/alerts/" + UUID.randomUUID() + "/acknowledge")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());
    }

    // ── 3.5 OPERATOR cannot generate enrollment token ────────────────────────

    @Test
    void operator_cannotGenerateEnrollmentToken() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin123");
        String deviceId = createDevice(adminToken, "rbac-enroll-" + System.nanoTime());

        String operatorToken = loginAndGetToken("operator", "op123");

        mockMvc.perform(post("/api/v1/devices/" + deviceId + "/enrollment-token")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());
    }

    // ── 3.6 OPERATOR can read device list ────────────────────────────────────

    @Test
    void operator_canReadDeviceList() throws Exception {
        String operatorToken = loginAndGetToken("operator", "op123");

        mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk());
    }

    // ── 3.7 No token is rejected ─────────────────────────────────────────────

    @Test
    void noToken_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isForbidden());
    }

    // ── 3.8 IDOR — foreign org JWT cannot access another org's device ─────────

    @Test
    void idor_foreignOrgJwt_cannotAccessOtherOrgsDevice() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin123");
        String deviceId = createDevice(adminToken, "idor-rbac-device-" + System.nanoTime());

        // Valid JWT but for a completely different (non-existent) org
        String foreignToken = jwtService.generateAccessToken("admin", "ADMIN", UUID.randomUUID());

        mockMvc.perform(get("/api/v1/devices/" + deviceId)
                        .header("Authorization", "Bearer " + foreignToken))
                .andExpect(status().isNotFound());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

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
}
