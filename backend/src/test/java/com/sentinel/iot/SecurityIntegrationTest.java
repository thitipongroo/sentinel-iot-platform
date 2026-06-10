package com.sentinel.iot;

import com.sentinel.iot.dto.DeviceRequest;
import com.sentinel.iot.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Security — integration smoke tests")
class SecurityIntegrationTest extends BaseIntegrationTest {

    private static final UUID TEST_ORG = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setTenantContext() {
        TenantContext.set(TEST_ORG);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ── Authentication ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Authentication")
    class Authentication {

        @SuppressWarnings("null")
        @Test
        @DisplayName("login returns an access token with the correct role claim")
        void login_producesJwtWithCorrectRole() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(authRequest("admin", "admin123"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.role").value("ADMIN"));
        }

        @Test
        @DisplayName("a valid access token grants access to protected endpoints")
        void protectedEndpoint_withValidToken_isAccessible() throws Exception {
            String token = loginAndGetToken("admin", "admin123");

            mockMvc.perform(get("/api/v1/devices")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("a tampered access token is rejected with HTTP 403")
        void protectedEndpoint_withTamperedToken_returns403() throws Exception {
            String tampered = loginAndGetToken("admin", "admin123") + "tampered";

            mockMvc.perform(get("/api/v1/devices")
                            .header("Authorization", "Bearer " + tampered))
                    .andExpect(status().isForbidden());
        }
    }

    // ── Role-based access ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Role-based access")
    class RoleBasedAccess {

        @SuppressWarnings("null")
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN can create a device")
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
        @DisplayName("OPERATOR is forbidden from creating a device")
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
        @DisplayName("OPERATOR can read the device list")
        void operatorCanReadDevices() throws Exception {
            mockMvc.perform(get("/api/v1/devices"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "OPERATOR")
        @DisplayName("OPERATOR can read the alert list")
        void operatorCanReadAlerts() throws Exception {
            mockMvc.perform(get("/api/v1/alerts"))
                    .andExpect(status().isOk());
        }
    }

    // ── Public endpoints ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Public endpoints")
    class PublicEndpoints {

        @Test
        @DisplayName("Swagger UI is publicly accessible without authentication")
        void swaggerUi_isPubliclyAccessible() throws Exception {
            mockMvc.perform(get("/swagger-ui.html"))
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("Prometheus actuator endpoint is publicly accessible")
        void prometheusEndpoint_isPubliclyAccessible() throws Exception {
            mockMvc.perform(get("/actuator/prometheus"))
                    .andExpect(status().isOk());
        }
    }

    // ── Request tracking ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Request tracking")
    class RequestTracking {

        @Test
        @DisplayName("a client-supplied X-Request-ID is echoed back in the response")
        void requestId_headerIsEchoedInResponse() throws Exception {
            mockMvc.perform(get("/actuator/health")
                            .header("X-Request-ID", "test-trace-123"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Request-ID", "test-trace-123"));
        }

        @Test
        @DisplayName("a generated UUID X-Request-ID is set in the response when no header is provided")
        void requestWithoutRequestId_responseStillContainsGeneratedId() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("X-Request-ID"));
        }
    }
}
