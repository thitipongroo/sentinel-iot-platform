package com.sentinel.iot;

import com.sentinel.iot.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * หมวดที่ 3 — Authorization / RBAC (8 tests)
 *
 * ทดสอบ: OPERATOR privilege escalation, unauthenticated access, IDOR cross-org.
 */
@DisplayName("RBAC Security — role-based access control")
class RbacSecurityTest extends BaseIntegrationTest {

    @Autowired JwtService jwtService;

    // ── Forbidden OPERATOR actions ────────────────────────────────────────────

    @Nested
    @DisplayName("Actions forbidden for OPERATOR")
    class ForbiddenOperatorActions {

        @SuppressWarnings("null")
        @Test
        @DisplayName("OPERATOR cannot create a device (requires ADMIN)")
        void operator_cannotCreateDevice() throws Exception {
            String token = loginAndGetToken("operator", "op123");

            mockMvc.perform(post("/api/v1/devices")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"blocked-device\"}"))
                    .andExpect(status().isForbidden());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("OPERATOR cannot patch device lifecycle (requires ADMIN)")
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

        @SuppressWarnings("null")
        @Test
        @DisplayName("OPERATOR cannot patch device firmware version (requires ADMIN)")
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

        @Test
        @DisplayName("OPERATOR cannot acknowledge an alert (requires ADMIN)")
        void operator_cannotAcknowledgeAlert() throws Exception {
            String operatorToken = loginAndGetToken("operator", "op123");

            // @PreAuthorize("hasRole('ADMIN')") on the endpoint — any UUID triggers the check
            mockMvc.perform(put("/api/v1/alerts/" + UUID.randomUUID() + "/acknowledge")
                            .header("Authorization", "Bearer " + operatorToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("OPERATOR cannot generate a device enrollment token (requires ADMIN)")
        void operator_cannotGenerateEnrollmentToken() throws Exception {
            String adminToken = loginAndGetToken("admin", "admin123");
            String deviceId = createDevice(adminToken, "rbac-enroll-" + System.nanoTime());

            String operatorToken = loginAndGetToken("operator", "op123");

            mockMvc.perform(post("/api/v1/devices/" + deviceId + "/enrollment-token")
                            .header("Authorization", "Bearer " + operatorToken))
                    .andExpect(status().isForbidden());
        }
    }

    // ── Allowed OPERATOR actions ──────────────────────────────────────────────

    @Nested
    @DisplayName("Actions allowed for OPERATOR")
    class AllowedOperatorActions {

        @Test
        @DisplayName("OPERATOR can read the device list")
        void operator_canReadDeviceList() throws Exception {
            String operatorToken = loginAndGetToken("operator", "op123");

            mockMvc.perform(get("/api/v1/devices")
                            .header("Authorization", "Bearer " + operatorToken))
                    .andExpect(status().isOk());
        }
    }

    // ── Unauthenticated requests ──────────────────────────────────────────────

    @Nested
    @DisplayName("Unauthenticated access")
    class Unauthenticated {

        @Test
        @DisplayName("request without an Authorization header is rejected")
        void noToken_isRejected() throws Exception {
            mockMvc.perform(get("/api/v1/devices"))
                    .andExpect(status().isForbidden());
        }
    }

    // ── Cross-org (IDOR) ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cross-org IDOR protection")
    class CrossOrgIdo {

        @Test
        @DisplayName("valid JWT with a foreign orgId cannot access another org's device")
        void idor_foreignOrgJwt_cannotAccessOtherOrgsDevice() throws Exception {
            String adminToken = loginAndGetToken("admin", "admin123");
            String deviceId = createDevice(adminToken, "idor-rbac-device-" + System.nanoTime());

            // Valid JWT but for a completely different (non-existent) org
            String foreignToken = jwtService.generateAccessToken("admin", "ADMIN", UUID.randomUUID());

            mockMvc.perform(get("/api/v1/devices/" + deviceId)
                            .header("Authorization", "Bearer " + foreignToken))
                    .andExpect(status().isNotFound());
        }
    }
}
