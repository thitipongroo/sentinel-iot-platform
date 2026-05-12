package com.sentinel.iot.regression;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.BaseIntegrationTest;
import com.sentinel.iot.dto.AuthRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 3.6 Database Migration Regression (5 tests)
 */
class MigrationRegressionTest extends BaseIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    // 3.6.1 — Flyway schema_history shows all migrations applied without error
    @Test
    void flywayMigrations_allAppliedSuccessfully() {
        Integer failedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false",
                Integer.class);
        assertThat(failedCount).isZero();

        Integer appliedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
                Integer.class);
        assertThat(appliedCount).isGreaterThan(0);
    }

    // 3.6.2 — Seed users (admin + operator) survive all migrations
    @Test
    void seedUsers_surviveAllMigrations() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest("admin", "admin123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest("operator", "op123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OPERATOR"));
    }

    // 3.6.3 — Row-Level Security is still enabled on tenant-scoped tables after migration
    @Test
    void rowLevelSecurity_isEnabledOnTenantTables() {
        List<String> rlsTables = jdbcTemplate.queryForList(
                "SELECT relname FROM pg_class " +
                "WHERE relname IN ('devices', 'alerts', 'audit_logs') " +
                "  AND relrowsecurity = true " +
                "ORDER BY relname",
                String.class);

        assertThat(rlsTables).containsExactlyInAnyOrder("devices", "alerts", "audit_logs");
    }

    // 3.6.4 — Key indexes are still present after migration
    @Test
    void keyIndexes_stillPresentAfterMigration() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes " +
                "WHERE tablename IN ('alerts', 'audit_logs') " +
                "  AND indexname IN ('idx_alert_org_id', 'idx_audit_logs_org_id') " +
                "ORDER BY indexname",
                String.class);

        assertThat(indexes).contains("idx_alert_org_id", "idx_audit_logs_org_id");
    }

    // 3.6.5 — Foreign key constraints are intact after migration
    @Test
    void foreignKeyConstraints_intactAfterMigration() {
        List<String> fkConstraints = jdbcTemplate.queryForList(
                "SELECT conname FROM pg_constraint " +
                "WHERE contype = 'f' " +
                "  AND conrelid::regclass::text IN " +
                "      ('devices', 'alerts', 'audit_logs', 'telemetry', 'refresh_tokens') " +
                "ORDER BY conname",
                String.class);

        assertThat(fkConstraints).isNotEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private AuthRequest authRequest(String username, String password) {
        AuthRequest req = new AuthRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }
}
