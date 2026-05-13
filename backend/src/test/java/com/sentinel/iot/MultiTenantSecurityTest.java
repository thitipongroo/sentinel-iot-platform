package com.sentinel.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sentinel.iot.dto.AuthRequest;
import com.sentinel.iot.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * หมวดที่ 4 — Multi-Tenant Isolation (6 tests)
 *
 * ทดสอบ: cross-tenant data leakage prevention, TenantContext per-request isolation,
 *        RLS policy existence, tampered JWT orgId claim rejection.
 *
 * NOTE: Tests 4.1–4.3 rely on application-layer org filtering (DeviceService uses
 * TenantContext in WHERE clauses). AlertService.getRecent() relies on PostgreSQL RLS.
 * Full RLS enforcement requires the DB user to be non-superuser (see V7 migration).
 */
class MultiTenantSecurityTest extends BaseIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbcTemplate;

    // ── 4.1 Foreign org JWT cannot see devices of another org ────────────────

    @Test
    void foreignOrgToken_cannotSeeDevicesOfOtherOrg() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin123");
        createDevice(adminToken, "tenant-device-" + System.nanoTime());

        // Valid JWT but signed with a random (non-existent) org UUID
        String foreignToken = jwtService.generateAccessToken("admin", "ADMIN", UUID.randomUUID());

        MvcResult result = mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + foreignToken))
                .andExpect(status().isOk())
                .andReturn();

        var devices = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(devices.isArray()).isTrue();
        // DeviceService.findAll() calls findAllByOrganizationId(foreignOrgId) → no matches
        assertThat(devices.size()).isEqualTo(0);
    }

    // ── 4.2 Foreign org JWT cannot see alerts of another org ─────────────────

    @Test
    void foreignOrgToken_cannotSeeAlertsOfOtherOrg() throws Exception {
        // AlertService.getRecent() relies on RLS (SET LOCAL app.org_id via TenantRlsAspect).
        // Spring Data JPA methods are @Transactional(readOnly=true) by default, triggering the aspect.
        String foreignToken = jwtService.generateAccessToken("admin", "ADMIN", UUID.randomUUID());

        MvcResult result = mockMvc.perform(get("/api/v1/alerts")
                        .header("Authorization", "Bearer " + foreignToken))
                .andExpect(status().isOk())
                .andReturn();

        var alerts = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(alerts.isArray()).isTrue();
        assertThat(alerts.size()).isEqualTo(0);
    }

    // ── 4.3 Foreign org JWT cannot access telemetry of another org's device ──

    @Test
    void foreignOrgToken_cannotAccessTelemetryOfOtherOrgDevice() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin123");
        String deviceId = createDevice(adminToken, "tenant-telem-" + System.nanoTime());

        String foreignToken = jwtService.generateAccessToken("admin", "ADMIN", UUID.randomUUID());

        // TelemetryController calls deviceService.findById(deviceId) for ownership check → 404
        mockMvc.perform(get("/api/v1/telemetry/" + deviceId + "/latest")
                        .header("Authorization", "Bearer " + foreignToken))
                .andExpect(status().isNotFound());
    }

    // ── 4.4 RLS tenant isolation policies exist on all tenant-scoped tables ──

    @Test
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

    // ── 4.5 TenantContext is isolated per request (no cross-request leakage) ─

    @Test
    void tenantContext_isIsolatedPerRequest() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin123");
        String deviceId = createDevice(adminToken, "ctx-isolation-" + System.nanoTime());

        // Foreign org request: TenantContext = randomUUID → no devices visible
        String foreignToken = jwtService.generateAccessToken("admin", "ADMIN", UUID.randomUUID());
        MvcResult foreignResult = mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + foreignToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(foreignResult.getResponse().getContentAsString()).size())
                .isEqualTo(0);

        // Subsequent request with admin token: TenantContext reset correctly → device is visible
        mockMvc.perform(get("/api/v1/devices/" + deviceId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // ── 4.6 Tampered orgId claim (payload modified, signature unchanged) → 403

    @Test
    void tamperedOrgIdInPayload_isRejected() throws Exception {
        String token = loginAndGetToken("admin", "admin123");
        String[] parts = token.split("\\.");

        // Decode payload JSON, replace orgId with a random UUID, re-encode WITHOUT re-signing
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

    // ── helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("null")
private String loginAndGetToken(String username, String password) throws Exception {
        AuthRequest req = new AuthRequest();
        req.setUsername(username);
        req.setPassword(password);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    @SuppressWarnings("null")
private String createDevice(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }
}
