package com.sentinel.iot;

import com.sentinel.iot.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("AlertController — alert retrieval and acknowledgment")
class AlertControllerIntegrationTest extends BaseIntegrationTest {

    private static final UUID TEST_ORG = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setTenantContext() {
        TenantContext.set(TEST_ORG);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ── GET endpoints ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Read endpoints")
    class ReadEndpoints {

        @SuppressWarnings("null")
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("GET /alerts returns a paginated result with 200")
        void getAlerts_returns200WithPage() throws Exception {
            mockMvc.perform(get("/api/v1/alerts")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        @SuppressWarnings("null")
        @Test
        @WithMockUser(roles = "OPERATOR")
        @DisplayName("GET /alerts/unacknowledged returns a list with 200")
        void getUnacknowledged_returns200WithList() throws Exception {
            mockMvc.perform(get("/api/v1/alerts/unacknowledged"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @SuppressWarnings("null")
        @Test
        @WithMockUser(roles = "OPERATOR")
        @DisplayName("GET /alerts/device/{deviceId} returns a list with 200")
        void getByDevice_returns200WithList() throws Exception {
            mockMvc.perform(get("/api/v1/alerts/device/{deviceId}", UUID.randomUUID()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    // ── Acknowledge endpoints (ADMIN only) ────────────────────────────────────

    @Nested
    @DisplayName("Acknowledge endpoints")
    class AcknowledgeEndpoints {

        @SuppressWarnings("null")
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("PUT /alerts/acknowledge-all returns 204 for ADMIN")
        void acknowledgeAll_adminRole_returns204() throws Exception {
            mockMvc.perform(put("/api/v1/alerts/acknowledge-all"))
                    .andExpect(status().isNoContent());
        }

        @SuppressWarnings("null")
        @Test
        @WithMockUser(roles = "OPERATOR")
        @DisplayName("PUT /alerts/acknowledge-all returns 403 for OPERATOR")
        void acknowledgeAll_operatorRole_returns403() throws Exception {
            mockMvc.perform(put("/api/v1/alerts/acknowledge-all"))
                    .andExpect(status().isForbidden());
        }
    }
}
