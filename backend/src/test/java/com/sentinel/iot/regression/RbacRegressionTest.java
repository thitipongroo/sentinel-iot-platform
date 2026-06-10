package com.sentinel.iot.regression;

import com.sentinel.iot.BaseIntegrationTest;
import com.sentinel.iot.dto.DeviceEnrollRequest;
import com.sentinel.iot.model.Alert;
import com.sentinel.iot.repository.AlertRepository;
import com.sentinel.iot.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 3.4 RBAC Rules Regression (10 tests)
 */
@DisplayName("RbacRegressionTest — role-based access control invariants")
class RbacRegressionTest extends BaseIntegrationTest {

    @Autowired JwtService       jwtService;
    @Autowired AlertRepository  alertRepository;

    // ── Operator restrictions ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Operator restrictions")
    class OperatorRestrictions {

        @SuppressWarnings("null")
        @Test
        @DisplayName("OPERATOR cannot create a device — returns 403")
        void createDevice_operator_returns403() throws Exception {
            String token = loginAndGetToken("operator", "op123");

            mockMvc.perform(post("/api/v1/devices")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("name", "rbac-op-" + System.nanoTime()))))
                    .andExpect(status().isForbidden());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("OPERATOR cannot PATCH device lifecycle — returns 403")
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

        @SuppressWarnings("null")
        @Test
        @DisplayName("OPERATOR cannot PATCH device firmware — returns 403")
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

        @Test
        @DisplayName("OPERATOR cannot acknowledge an alert — returns 403")
        void acknowledgeAlert_operator_returns403() throws Exception {
            String adminToken    = loginAndGetToken("admin", "admin123");
            String operatorToken = loginAndGetToken("operator", "op123");
            UUID alertId = createAlert(adminToken);

            mockMvc.perform(put("/api/v1/alerts/" + alertId + "/acknowledge")
                            .header("Authorization", "Bearer " + operatorToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("OPERATOR cannot generate an enrollment token — returns 403")
        void generateEnrollmentToken_operator_returns403() throws Exception {
            String adminToken    = loginAndGetToken("admin", "admin123");
            String operatorToken = loginAndGetToken("operator", "op123");
            String deviceId      = createDevice(adminToken, "enroll-rbac-" + System.nanoTime());

            mockMvc.perform(post("/api/v1/devices/" + deviceId + "/enrollment-token")
                            .header("Authorization", "Bearer " + operatorToken))
                    .andExpect(status().isForbidden());
        }
    }

    // ── Operator permissions ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Operator read permissions")
    class OperatorPermissions {

        @Test
        @DisplayName("OPERATOR can read the device list — returns 200")
        void readDevices_operator_returns200() throws Exception {
            String token = loginAndGetToken("operator", "op123");

            mockMvc.perform(get("/api/v1/devices")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("OPERATOR can read alerts — returns 200")
        void readAlerts_operator_returns200() throws Exception {
            String token = loginAndGetToken("operator", "op123");

            mockMvc.perform(get("/api/v1/alerts")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    // ── Admin privileges ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Admin privileges")
    class AdminPrivileges {

        @SuppressWarnings("null")
        @Test
        @DisplayName("ADMIN can create a device — returns 201")
        void createDevice_admin_returns201() throws Exception {
            String token = loginAndGetToken("admin", "admin123");

            mockMvc.perform(post("/api/v1/devices")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("name", "rbac-admin-" + System.nanoTime()))))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("ADMIN can acknowledge an alert — returns 204")
        void acknowledgeAlert_admin_returns204() throws Exception {
            String adminToken = loginAndGetToken("admin", "admin123");
            UUID alertId = createAlert(adminToken);

            mockMvc.perform(put("/api/v1/alerts/" + alertId + "/acknowledge")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNoContent());
        }
    }

    // ── Enrollment endpoint ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Enrollment endpoint")
    class EnrollmentEndpoint {

        @SuppressWarnings("null")
        @Test
        @DisplayName("enroll endpoint with an invalid token returns 400 Bad Request")
        void enrollEndpoint_withInvalidToken_returns400() throws Exception {
            DeviceEnrollRequest req = new DeviceEnrollRequest(UUID.randomUUID(), "not-a-real-token", null);

            mockMvc.perform(post("/api/v1/devices/enroll")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── private helper (uses AlertRepository — unique to this file) ───────────

    private UUID createAlert(String adminToken) throws Exception {
        UUID orgId    = jwtService.extractOrgId(adminToken);
        String deviceId = createDevice(adminToken, "alert-device-" + System.nanoTime());
        Alert alert = new Alert(UUID.fromString(deviceId), "WARNING", "rbac-test alert", orgId);
        return alertRepository.save(alert).getId();
    }
}
