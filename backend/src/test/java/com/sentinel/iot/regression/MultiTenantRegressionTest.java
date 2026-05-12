package com.sentinel.iot.regression;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.BaseIntegrationTest;
import com.sentinel.iot.dto.AuthRequest;
import com.sentinel.iot.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 3.5 Multi-Tenant Isolation Regression (6 tests)
 */
class MultiTenantRegressionTest extends BaseIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbcTemplate;

    // 3.5.1 — Org B JWT → GET /devices of org A returns empty array
    @Test
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

    // 3.5.2 — Org B JWT → GET /devices/{id of org A} returns 404
    @Test
    void deviceDetail_foreignOrg_returns404() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin123");
        String deviceId   = createDevice(adminToken, "mt-detail-" + System.nanoTime());

        String foreignToken = jwtService.generateAccessToken("admin", "ADMIN", UUID.randomUUID());

        mockMvc.perform(get("/api/v1/devices/" + deviceId)
                        .header("Authorization", "Bearer " + foreignToken))
                .andExpect(status().isNotFound());
    }

    // 3.5.3 — Org B JWT → GET /telemetry/{device of org A}/latest returns 404
    @Test
    void telemetry_foreignOrg_returns404() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin123");
        String deviceId   = createDevice(adminToken, "mt-telem-" + System.nanoTime());

        String foreignToken = jwtService.generateAccessToken("admin", "ADMIN", UUID.randomUUID());

        mockMvc.perform(get("/api/v1/telemetry/" + deviceId + "/latest")
                        .header("Authorization", "Bearer " + foreignToken))
                .andExpect(status().isNotFound());
    }

    // 3.5.4 — Org B JWT → GET /alerts returns empty array
    @Test
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

    // 3.5.5 — RLS policies still present for all tenant-scoped tables
    @Test
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

    // 3.5.6 — Device created by org A admin has organizationId = org A's UUID
    @Test
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

    // ── helpers ───────────────────────────────────────────────────────────────

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
