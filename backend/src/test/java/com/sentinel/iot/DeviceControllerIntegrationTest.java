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

@DisplayName("DeviceController — CRUD and role-based access")
class DeviceControllerIntegrationTest extends BaseIntegrationTest {

    private static final UUID TEST_ORG = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setTenantContext() {
        TenantContext.set(TEST_ORG);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Create and fetch")
    class CreateAndFetch {

        @SuppressWarnings("null")
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN can create a device and the new device appears in the device list")
        void shouldCreateAndFetchDevice() throws Exception {
            DeviceRequest req = new DeviceRequest();
            req.setName("integration-sensor");
            req.setLocation("Test Lab");

            mockMvc.perform(post("/api/v1/devices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("integration-sensor"));

            mockMvc.perform(get("/api/v1/devices"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == 'integration-sensor')]").exists());
        }
    }

    // ── Role-based access ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Role-based access")
    class RoleBasedAccess {

        @SuppressWarnings("null")
        @Test
        @WithMockUser(roles = "OPERATOR")
        @DisplayName("OPERATOR is forbidden from creating a device")
        void operatorCannotCreateDevice() throws Exception {
            DeviceRequest req = new DeviceRequest();
            req.setName("unauthorized-device");

            mockMvc.perform(post("/api/v1/devices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }
    }
}
