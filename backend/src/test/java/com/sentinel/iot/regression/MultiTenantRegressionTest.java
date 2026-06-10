package com.sentinel.iot.regression;

import com.sentinel.iot.BaseIntegrationTest;
import com.sentinel.iot.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 3.5 Multi-Tenant Isolation Regression (6 tests)
 */
@DisplayName("MultiTenantRegressionTest — tenant isolation invariants")
class MultiTenantRegressionTest extends BaseIntegrationTest {

    @Autowired JwtService  jwtService;
    @Autowired JdbcTemplate jdbcTemplate;

    // ── Cross-org data leakage ────────────────────────────────────────────────

    @Nested
    @DisplayName("Cross-org data leakage prevention")
    class CrossOrgDataLeakage {

        @Test
        @DisplayName("foreign-org token sees empty device list for a tenant that has devices")
        void deviceList_foreignOrg_returnsEmpty() throws Exception {
            String adminToken = loginAndGetToken("admin", "admin123");
            createDevice(adminToken, "mt-device-" + System.nanoTime());

            String foreignToken = jwtService.generateAccessToken("admin", "ADMIN", UUID.randomUUID());

            MvcResult result = mockMvc.perform(get("/api/v1/devices")
                            .header("Authorization", "Bearer " + foreignToken))
                    .andExpect(status().isOk())
                    .andReturn();

            var devices = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(devices.isArray()).isTrue();
            assertThat(devices.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("foreign-org token requesting a specific device by id returns 404")
        void deviceDetail_foreignOrg_returns404() throws Exception {
            String adminToken = loginAndGetToken("admin", "admin123");
            String deviceId   = createDevice(adminToken, "mt-detail-" + System.nanoTime());

            String foreignToken = jwtService.generateAccessToken("admin", "ADMIN", UUID.randomUUID());

            mockMvc.perform(get("/api/v1/devices/" + deviceId)
                            .header("Authorization", "Bearer " + foreignToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("foreign-org token requesting telemetry for a cross-tenant device returns 404")
        void telemetry_foreignOrg_returns404() throws Exception {
            String adminToken = loginAndGetToken("admin", "admin123");
            String deviceId   = createDevice(adminToken, "mt-telem-" + System.nanoTime());

            String foreignToken = jwtService.generateAccessToken("admin", "ADMIN", UUID.randomUUID());

            mockMvc.perform(get("/api/v1/telemetry/" + deviceId + "/latest")
                            .header("Authorization", "Bearer " + foreignToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("foreign-org token sees empty alert list even when the tenant has alerts")
        void alertList_foreignOrg_returnsEmpty() throws Exception {
            String foreignToken = jwtService.generateAccessToken("admin", "ADMIN", UUID.randomUUID());

            MvcResult result = mockMvc.perform(get("/api/v1/alerts")
                            .header("Authorization", "Bearer " + foreignToken))
                    .andExpect(status().isOk())
                    .andReturn();

            var alerts = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(alerts.isArray()).isTrue();
            assertThat(alerts.size()).isEqualTo(0);
        }
    }

    // ── RLS infrastructure ────────────────────────────────────────────────────

    @Nested
    @DisplayName("RLS policy infrastructure")
    class RlsInfrastructure {

        @Test
        @DisplayName("tenant isolation policies exist on devices, alerts, and audit_logs")
        void rlsPolicies_existForAllTenantTables() {
            List<String> policies = jdbcTemplate.queryForList(
                    "SELECT policyname FROM pg_policies " +
                    "WHERE tablename IN ('devices','alerts','audit_logs') " +
                    "ORDER BY policyname",
                    String.class);

            assertThat(policies).contains(
                    "devices_tenant_isolation",
                    "alerts_tenant_isolation",
                    "audit_logs_tenant_isolation");
        }
    }

    // ── Organization binding ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Organization binding")
    class OrganizationBinding {

        @Test
        @DisplayName("device created by org-A admin carries org-A's UUID in organizationId")
        void createdDevice_hasCorrectOrganizationId() throws Exception {
            String adminToken = loginAndGetToken("admin", "admin123");
            UUID   orgId      = jwtService.extractOrgId(adminToken);
            String deviceId   = createDevice(adminToken, "mt-org-check-" + System.nanoTime());

            MvcResult result = mockMvc.perform(get("/api/v1/devices/" + deviceId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseOrgId = objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("organizationId").asText();
            assertThat(responseOrgId).isEqualTo(orgId.toString());
        }
    }
}
