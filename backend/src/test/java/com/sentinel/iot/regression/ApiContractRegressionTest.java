package com.sentinel.iot.regression;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinel.iot.BaseIntegrationTest;
import com.sentinel.iot.model.DeviceLifecycleStatus;
import com.sentinel.iot.dto.DeviceLifecycleRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 3.1 API Contract Regression (8 tests) + 3.2 HTTP Status Code Regression (10 tests)
 */
@DisplayName("ApiContractRegressionTest — API contract and HTTP status codes")
class ApiContractRegressionTest extends BaseIntegrationTest {

    // ── 3.1 API Contract ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("3.1 API contract schema")
    class ApiContractSchema {

        @SuppressWarnings("null")
        @Test
        @DisplayName("login response has accessToken / role / username and omits refreshToken")
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
        @DisplayName("GET /devices returns array with id, name, status, lifecycleStatus, organizationId fields")
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
        @DisplayName("GET /devices/{id} response includes firmwareVersion and organizationId")
        void deviceDetailSchema_hasAllRequiredFields() throws Exception {
            String token    = loginAndGetToken("admin", "admin123");
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
        @DisplayName("GET /alerts returns array with id, deviceId, level, message, acknowledged, createdAt, organizationId")
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
        @DisplayName("GET /telemetry/stats returns exactly {lastMinute, replayQueueSize}")
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
        @DisplayName("404 error response has type, status, detail fields and omits stackTrace")
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

        @SuppressWarnings("null")
        @Test
        @DisplayName("400 validation error response has ProblemDetail format and omits stackTrace")
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
        @DisplayName("GET /devices returns a JSON array")
        void deviceListEndpoint_isAnArray() throws Exception {
            String token = loginAndGetToken("admin", "admin123");

            mockMvc.perform(get("/api/v1/devices")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    // ── 3.2 HTTP Status Code Regression ──────────────────────────────────────

    @Nested
    @DisplayName("3.2 HTTP status code invariants")
    class HttpStatusCodes {

        @SuppressWarnings("null")
        @Test
        @DisplayName("valid credentials → 200 OK")
        void validLogin_returns200() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(authRequest("admin", "admin123"))))
                    .andExpect(status().isOk());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("wrong password → 401 Unauthorized")
        void invalidLogin_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(authRequest("admin", "wrongpassword"))))
                    .andExpect(status().isUnauthorized());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("ADMIN creates device → 201 Created")
        void createDevice_asAdmin_returns201() throws Exception {
            String token = loginAndGetToken("admin", "admin123");

            mockMvc.perform(post("/api/v1/devices")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("name", "status-check-" + System.nanoTime()))))
                    .andExpect(status().isCreated());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("OPERATOR creates device → 403 Forbidden")
        void createDevice_asOperator_returns403() throws Exception {
            String token = loginAndGetToken("operator", "op123");

            mockMvc.perform(post("/api/v1/devices")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("name", "op-device-" + System.nanoTime()))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /devices/{random-uuid} → 404 Not Found")
        void getDevice_withRandomUuid_returns404() throws Exception {
            String token = loginAndGetToken("admin", "admin123");

            mockMvc.perform(get("/api/v1/devices/" + UUID.randomUUID())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("blank device name → 400 Bad Request")
        void createDevice_withBlankName_returns400() throws Exception {
            String token = loginAndGetToken("admin", "admin123");

            mockMvc.perform(post("/api/v1/devices")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("name", ""))))
                    .andExpect(status().isBadRequest());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("re-activate a DECOMMISSIONED device → 400 Bad Request")
        void patchLifecycle_onDecommissionedDevice_returns400() throws Exception {
            String token    = loginAndGetToken("admin", "admin123");
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

        @SuppressWarnings("null")
        @Test
        @DisplayName("malformed JSON body → 400 Bad Request")
        void malformedJsonBody_returns400() throws Exception {
            String token = loginAndGetToken("admin", "admin123");

            mockMvc.perform(post("/api/v1/devices")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("not-valid-json"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /auth/refresh with invalid cookie value → 400 Bad Request")
        void refreshWithInvalidCookie_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new jakarta.servlet.http.Cookie("sentinel_refresh_token", "invalid-token-value")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("unauthenticated request to protected endpoint → 403 Forbidden")
        void unauthenticatedRequest_returns403() throws Exception {
            mockMvc.perform(get("/api/v1/devices"))
                    .andExpect(status().isForbidden());
        }
    }
}
