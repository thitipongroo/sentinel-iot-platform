package com.sentinel.iot.regression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.BaseIntegrationTest;
import com.sentinel.iot.dto.AuthRequest;
import com.sentinel.iot.dto.DeviceLifecycleRequest;
import com.sentinel.iot.model.DeviceLifecycleStatus;
import com.sentinel.iot.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 3.1 API Contract Regression (8 tests) + 3.2 HTTP Status Code Regression (10 tests)
 */
class ApiContractRegressionTest extends BaseIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;

    // ── 3.1 API Contract ──────────────────────────────────────────────────────

    @Test
    void loginResponseSchema_hasRequiredFieldsAndNoRefreshTokenInBody() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest("admin", "admin123"))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("accessToken")).isTrue();
        assertThat(body.has("role")).isTrue();
        assertThat(body.has("username")).isTrue();
        assertThat(body.has("refreshToken")).isFalse();
    }

    @Test
    void deviceListSchema_hasRequiredFields() throws Exception {
        String token = loginAndGetToken("admin", "admin123");

        MvcResult result = mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        JsonNode devices = objectMapper.readTree(result.getResponse().getContentAsString());
        if (!devices.isEmpty()) {
            JsonNode device = devices.get(0);
            assertThat(device.has("id")).isTrue();
            assertThat(device.has("name")).isTrue();
            assertThat(device.has("status")).isTrue();
            assertThat(device.has("lifecycleStatus")).isTrue();
            assertThat(device.has("organizationId")).isTrue();
        }
    }

    @Test
    void deviceDetailSchema_hasAllRequiredFields() throws Exception {
        String token = loginAndGetToken("admin", "admin123");
        String deviceId = createDevice(token, "schema-check-" + System.nanoTime());

        MvcResult result = mockMvc.perform(get("/api/v1/devices/" + deviceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode device = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(device.has("id")).isTrue();
        assertThat(device.has("name")).isTrue();
        assertThat(device.has("status")).isTrue();
        assertThat(device.has("lifecycleStatus")).isTrue();
        assertThat(device.has("firmwareVersion")).isTrue();
        assertThat(device.has("organizationId")).isTrue();
    }

    @Test
    void alertListSchema_hasRequiredFields() throws Exception {
        String token = loginAndGetToken("admin", "admin123");

        MvcResult result = mockMvc.perform(get("/api/v1/alerts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        JsonNode alerts = objectMapper.readTree(result.getResponse().getContentAsString());
        if (!alerts.isEmpty()) {
            JsonNode alert = alerts.get(0);
            assertThat(alert.has("id")).isTrue();
            assertThat(alert.has("deviceId")).isTrue();
            assertThat(alert.has("level")).isTrue();
            assertThat(alert.has("message")).isTrue();
            assertThat(alert.has("acknowledged")).isTrue();
            assertThat(alert.has("createdAt")).isTrue();
            assertThat(alert.has("organizationId")).isTrue();
        }
    }

    @Test
    void telemetryStatsSchema_hasOnlyExpectedFields() throws Exception {
        String token = loginAndGetToken("admin", "admin123");

        MvcResult result = mockMvc.perform(get("/api/v1/telemetry/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode stats = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(stats.has("lastMinute")).isTrue();
        assertThat(stats.has("replayQueueSize")).isTrue();
        assertThat(stats.size()).isEqualTo(2);
    }

    @Test
    void errorResponseSchema_hasProblemDetailFields_neverStackTrace() throws Exception {
        String token = loginAndGetToken("admin", "admin123");

        MvcResult result = mockMvc.perform(get("/api/v1/devices/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andReturn();

        JsonNode error = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(error.has("type")).isTrue();
        assertThat(error.has("status")).isTrue();
        assertThat(error.has("detail")).isTrue();
        assertThat(error.has("stackTrace")).isFalse();
    }

    @Test
    void errorResponseSchema_400_hasProblemDetailFormat() throws Exception {
        String token = loginAndGetToken("admin", "admin123");

        MvcResult result = mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", ""))))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode error = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(error.has("type")).isTrue();
        assertThat(error.has("status")).isTrue();
        assertThat(error.has("detail")).isTrue();
        assertThat(error.has("stackTrace")).isFalse();
    }

    @Test
    void deviceListEndpoint_isAnArray() throws Exception {
        String token = loginAndGetToken("admin", "admin123");

        mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ── 3.2 HTTP Status Code Regression ──────────────────────────────────────

    @Test
    void validLogin_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest("admin", "admin123"))))
                .andExpect(status().isOk());
    }

    @Test
    void invalidLogin_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest("admin", "wrongpassword"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createDevice_asAdmin_returns201() throws Exception {
        String token = loginAndGetToken("admin", "admin123");

        mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "status-check-" + System.nanoTime()))))
                .andExpect(status().isCreated());
    }

    @Test
    void createDevice_asOperator_returns403() throws Exception {
        String token = loginAndGetToken("operator", "op123");

        mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "op-device-" + System.nanoTime()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDevice_withRandomUuid_returns404() throws Exception {
        String token = loginAndGetToken("admin", "admin123");

        mockMvc.perform(get("/api/v1/devices/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void createDevice_withBlankName_returns400() throws Exception {
        String token = loginAndGetToken("admin", "admin123");

        mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchLifecycle_onDecommissionedDevice_returns400() throws Exception {
        String token = loginAndGetToken("admin", "admin123");
        String deviceId = createDevice(token, "decom-test-" + System.nanoTime());

        DeviceLifecycleRequest decommission = new DeviceLifecycleRequest();
        decommission.setLifecycleStatus(DeviceLifecycleStatus.DECOMMISSIONED);

        mockMvc.perform(patch("/api/v1/devices/" + deviceId + "/lifecycle")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decommission)))
                .andExpect(status().isOk());

        DeviceLifecycleRequest reactivate = new DeviceLifecycleRequest();
        reactivate.setLifecycleStatus(DeviceLifecycleStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/devices/" + deviceId + "/lifecycle")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reactivate)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonBody_returns400() throws Exception {
        String token = loginAndGetToken("admin", "admin123");

        mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-valid-json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshWithInvalidCookie_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("sentinel_refresh_token", "invalid-token-value")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedRequest_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isForbidden());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest(username, password))))
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

    private AuthRequest authRequest(String username, String password) {
        AuthRequest req = new AuthRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }
}
