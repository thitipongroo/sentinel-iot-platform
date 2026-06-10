package com.sentinel.iot.regression;

import com.sentinel.iot.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 3.6 Database Migration Regression (5 tests)
 */
@DisplayName("MigrationRegressionTest — database schema integrity")
class MigrationRegressionTest extends BaseIntegrationTest {

    @Autowired JdbcTemplate jdbcTemplate;

    // ── Flyway history ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Flyway history")
    class FlywayHistory {

        @Test
        @DisplayName("all Flyway migrations in schema_history have success=true and count > 0")
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
    }

    // ── Seed data ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Seed data survival")
    class SeedData {

        @SuppressWarnings("null")
        @Test
        @DisplayName("seed users admin (ADMIN) and operator (OPERATOR) survive all migrations")
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
    }

    // ── Row-Level Security ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Row-Level Security")
    class RowLevelSecurity {

        @Test
        @DisplayName("RLS is still enabled on devices, alerts, and audit_logs after migration")
        void rowLevelSecurity_isEnabledOnTenantTables() {
            List<String> rlsTables = jdbcTemplate.queryForList(
                    "SELECT relname FROM pg_class " +
                    "WHERE relname IN ('devices', 'alerts', 'audit_logs') " +
                    "  AND relrowsecurity = true " +
                    "ORDER BY relname",
                    String.class);

            assertThat(rlsTables).containsExactlyInAnyOrder("devices", "alerts", "audit_logs");
        }
    }

    // ── Schema constraints ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Schema constraints and indexes")
    class SchemaConstraints {

        @Test
        @DisplayName("idx_alert_org_id and idx_audit_logs_org_id are present after migration")
        void keyIndexes_stillPresentAfterMigration() {
            List<String> indexes = jdbcTemplate.queryForList(
                    "SELECT indexname FROM pg_indexes " +
                    "WHERE tablename IN ('alerts', 'audit_logs') " +
                    "  AND indexname IN ('idx_alert_org_id', 'idx_audit_logs_org_id') " +
                    "ORDER BY indexname",
                    String.class);

            assertThat(indexes).contains("idx_alert_org_id", "idx_audit_logs_org_id");
        }

        @Test
        @DisplayName("foreign key constraints on devices, alerts, audit_logs, telemetry, refresh_tokens are intact")
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
    }
}
