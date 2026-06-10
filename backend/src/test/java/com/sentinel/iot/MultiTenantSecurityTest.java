package com.sentinel.iot;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sentinel.iot.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * หมวดที่ 4 — Multi-Tenant Isolation (6 tests)
 *
 * ทดสอบ: cross-tenant data leakage prevention, TenantContext per-request isolation,
 *        RLS policy existence, tampered JWT orgId claim rejection.
 */
@DisplayName("Multi-Tenant Security — data isolation")
class MultiTenantSecurityTest extends BaseIntegrationTest {

    @Autowired JwtService    jwtService;
    @Autowired JdbcTemplate  jdbcTemplate;

    // ── Data leakage prevention ───────────────────────────────────────────────

    @Nested
    @DisplayName("Data leakage prevention")
    class DataLeakagePrevention {

        @Test
        @DisplayName("foreign-org JWT cannot see devices that belong to another org")
        void foreignOrgToken_cannotSeeDevicesOfOtherOrg() throws Exception {
            String adminToken = loginAndGetToken("admin", "admin123");
            createDevice(adminToken, "tenant-device-" + System.nanoTime());

            // Valid JWT but signed with a random (non-existent) org UUID
            String foreignToken = jwtService.generateAccessToken("admin", "ADMIN", UUID.randomUUID());

            @SuppressWarnings("null")
            var result = mockMvc.perform(get("/api/v1/devices")
                            .header("Authorization", "Bearer " + foreignToken))
                    .andExpect(status().isOk())
                    .andReturn();

            var devices = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(devices.isArray()).isTrue();
            // DeviceService.findAll() calls findAllByOrganizationId(foreignOrgId) → no matches
            assertThat(devices.size()).as("foreign org must see 0 devices").isEqualTo(0);
        }

        @Test
        @DisplayName("foreign-org JWT cannot see alerts that belong to another org")
        void foreignOrgToken_cannotSeeAlertsOfOtherOrg() throws Exception {
            String foreignToken = jwtService.generateAccessToken("admin", "ADMIN", UUID.randomUUID());

            @SuppressWarnings("null")
            var result = mockMvc.perform(get("/api/v1/alerts")
                            .header("Authorization", "Bearer " + foreignToken))
                    .andExpect(status().isOk())
                    .andReturn();

            var alerts = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(alerts.isArray()).isTrue();
            assertThat(alerts.size()).as("foreign org must see 0 alerts").isEqualTo(0);
        }

        @Test
        @DisplayName("foreign-org JWT cannot access telemetry for a device owned by another org")
        void foreignOrgToken_cannotAccessTelemetryOfOtherOrgDevice() throws Exception {
            String adminToken = loginAndGetToken("admin", "admin123");
            String deviceId = createDevice(adminToken, "tenant-telem-" + System.nanoTime());

            String foreignToken = jwtService.generateAccessToken("admin", "ADMIN", UUID.randomUUID());

            // TelemetryController checks device ownership → 404 for foreign org
            mockMvc.perform(get("/api/v1/telemetry/" + deviceId + "/latest")
                            .header("Authorization", "Bearer " + foreignToken))
                    .andExpect(status().isNotFound());
        }
    }

    // ── RLS policy infrastructure ─────────────────────────────────────────────

    @Nested
    @DisplayName("RLS policy infrastructure")
    class RlsPolicyInfrastructure {

        @Test
        @DisplayName("tenant isolation RLS policies exist on devices, alerts, and audit_logs tables")
        void rlsPolicies_existForTenantTables() {
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

    // ── Context isolation and token integrity ─────────────────────────────────

    @Nested
    @DisplayName("Context isolation and token integrity")
    class ContextIsolationAndTokenIntegrity {

        @Test
        @DisplayName("TenantContext is reset between requests — no cross-request data leakage")
        void tenantContext_isIsolatedPerRequest() throws Exception {
            String adminToken = loginAndGetToken("admin", "admin123");
            String deviceId = createDevice(adminToken, "ctx-isolation-" + System.nanoTime());

            // Foreign org request: TenantContext = randomUUID → no devices visible
            String foreignToken = jwtService.generateAccessToken("admin", "ADMIN", UUID.randomUUID());
            @SuppressWarnings("null")
            var foreignResult = mockMvc.perform(get("/api/v1/devices")
                            .header("Authorization", "Bearer " + foreignToken))
                    .andExpect(status().isOk())
                    .andReturn();
            assertThat(objectMapper.readTree(foreignResult.getResponse().getContentAsString()).size())
                    .as("foreign request must see 0 devices").isEqualTo(0);

            // Subsequent admin request: TenantContext reset correctly → device is visible
            mockMvc.perform(get("/api/v1/devices/" + deviceId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("modifying the orgId claim without re-signing is rejected (signature mismatch)")
        void tamperedOrgIdInPayload_isRejected() throws Exception {
            String token = loginAndGetToken("admin", "admin123");
            String[] parts = token.split("\\.");

            // Decode payload, replace orgId, re-encode WITHOUT re-signing
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            ObjectNode payload = (ObjectNode) objectMapper.readTree(payloadBytes);
            payload.put("orgId", UUID.randomUUID().toString());

            String tamperedPart = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(payload));
            String tamperedToken = parts[0] + "." + tamperedPart + "." + parts[2];

            // Signature no longer matches modified payload → 403
            mockMvc.perform(get("/api/v1/devices")
                            .header("Authorization", "Bearer " + tamperedToken))
                    .andExpect(status().isForbidden());
        }
    }
}
