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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("TelemetryController — telemetry retrieval and system stats")
class TelemetryControllerIntegrationTest extends BaseIntegrationTest {

    private static final UUID TEST_ORG = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setTenantContext() {
        TenantContext.set(TEST_ORG);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ── /stats ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Stats endpoint")
    class StatsEndpoint {

        @SuppressWarnings("null")
        @Test
        @WithMockUser(roles = "OPERATOR")
        @DisplayName("GET /telemetry/stats returns lastMinute and replayQueueSize")
        void stats_returnsExpectedKeys() throws Exception {
            mockMvc.perform(get("/api/v1/telemetry/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lastMinute").isNumber())
                    .andExpect(jsonPath("$.replayQueueSize").isNumber());
        }
    }

    // ── Device-scoped endpoints ───────────────────────────────────────────────

    @Nested
    @DisplayName("Device-scoped endpoints")
    class DeviceScopedEndpoints {

        @SuppressWarnings("null")
        @Test
        @WithMockUser(roles = "OPERATOR")
        @DisplayName("GET /telemetry/{id}/latest returns 404 when device does not exist")
        void getLatest_deviceNotFound_returns404() throws Exception {
            mockMvc.perform(get("/api/v1/telemetry/{deviceId}/latest", UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }

        @SuppressWarnings("null")
        @Test
        @WithMockUser(roles = "OPERATOR")
        @DisplayName("GET /telemetry/{id}/cache returns 404 when device does not exist")
        void getCache_deviceNotFound_returns404() throws Exception {
            mockMvc.perform(get("/api/v1/telemetry/{deviceId}/cache", UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }
    }
}
