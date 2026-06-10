package com.sentinel.iot;

import com.sentinel.iot.dto.UpdateSettingsRequest;
import com.sentinel.iot.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("SettingsController — platform settings read and update")
class SettingsControllerIntegrationTest extends BaseIntegrationTest {

    private static final UUID TEST_ORG = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setTenantContext() {
        TenantContext.set(TEST_ORG);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ── Get settings ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Get settings")
    class GetSettings {

        @SuppressWarnings("null")
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN can read settings; response contains thresholds, retention, and notifications keys")
        void getSettings_adminRole_returnsSettingsMap() throws Exception {
            mockMvc.perform(get("/api/v1/settings"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.thresholds").exists())
                    .andExpect(jsonPath("$.retention").exists())
                    .andExpect(jsonPath("$.notifications").exists());
        }

        @SuppressWarnings("null")
        @Test
        @WithMockUser(roles = "OPERATOR")
        @DisplayName("OPERATOR can also read settings")
        void getSettings_operatorRole_returns200() throws Exception {
            mockMvc.perform(get("/api/v1/settings"))
                    .andExpect(status().isOk());
        }
    }

    // ── Update settings ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Update settings")
    class UpdateSettings {

        @SuppressWarnings("null")
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN can update settings and response reflects the new threshold values")
        void updateSettings_adminRole_returns200() throws Exception {
            UpdateSettingsRequest req = new UpdateSettingsRequest(
                    85.0, 90.0, 300.0, 30, 90, true, false, false);

            mockMvc.perform(patch("/api/v1/settings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.thresholds.temperatureCelsius").value(85.0));
        }

        @SuppressWarnings("null")
        @Test
        @WithMockUser(roles = "OPERATOR")
        @DisplayName("OPERATOR is forbidden from updating settings")
        void updateSettings_operatorRole_returns403() throws Exception {
            UpdateSettingsRequest req = new UpdateSettingsRequest(
                    85.0, 90.0, 300.0, 30, 90, false, false, false);

            mockMvc.perform(patch("/api/v1/settings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }
    }
}
