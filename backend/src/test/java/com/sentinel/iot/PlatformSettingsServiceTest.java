package com.sentinel.iot;

import com.sentinel.iot.dto.UpdateSettingsRequest;
import com.sentinel.iot.model.PlatformSettings;
import com.sentinel.iot.repository.PlatformSettingsRepository;
import com.sentinel.iot.service.PlatformSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Tag("unit")
@DisplayName("PlatformSettingsService")
@ExtendWith(MockitoExtension.class)
class PlatformSettingsServiceTest {

    @Mock PlatformSettingsRepository repo;

    @InjectMocks PlatformSettingsService service;

    private final UUID orgId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @SuppressWarnings("null")
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "defaultTemp",      80.0);
        ReflectionTestUtils.setField(service, "defaultHum",       90.0);
        ReflectionTestUtils.setField(service, "defaultSmoke",    200.0);
        ReflectionTestUtils.setField(service, "defaultTelDays",    30);
        ReflectionTestUtils.setField(service, "defaultAuditDays",  90);
    }

    // ── getOrDefault ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getOrDefault")
    class GetOrDefault {

        @SuppressWarnings("null")
        @Test
        @DisplayName("returns the persisted settings object when one exists for the organisation")
        void getOrDefault_returnsExistingSettings_whenFound() {
            PlatformSettings existing = new PlatformSettings();
            existing.setOrganizationId(orgId);
            existing.setTemperatureThreshold(75.0);
            when(repo.findById(orgId)).thenReturn(Optional.of(existing));

            PlatformSettings result = service.getOrDefault(orgId);

            assertThat(result).as("must return the same instance").isSameAs(existing);
            assertThat(result.getTemperatureThreshold()).as("temperature threshold").isEqualTo(75.0);
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("returns a default-populated settings object when no record exists for the organisation")
        void getOrDefault_returnsDefaultValues_whenSettingsNotFound() {
            when(repo.findById(orgId)).thenReturn(Optional.empty());

            PlatformSettings result = service.getOrDefault(orgId);

            assertThat(result.getOrganizationId()).as("organizationId").isEqualTo(orgId);
            assertThat(result.getTemperatureThreshold()).as("default temperature threshold").isEqualTo(80.0);
            assertThat(result.getHumidityThreshold()).as("default humidity threshold").isEqualTo(90.0);
            assertThat(result.getSmokeThreshold()).as("default smoke threshold").isEqualTo(200.0);
            assertThat(result.getTelemetryRetentionDays()).as("default telemetry retention").isEqualTo(30);
            assertThat(result.getAuditRetentionDays()).as("default audit retention").isEqualTo(90);
            assertThat(result.isSlackEnabled()).as("slack disabled by default").isFalse();
            assertThat(result.isLineEnabled()).as("line disabled by default").isFalse();
            assertThat(result.isWebhookEnabled()).as("webhook disabled by default").isFalse();
        }
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("update")
    class Update {

        @SuppressWarnings("null")
        @Test
        @DisplayName("persists all request fields including thresholds, retention days, and notification flags")
        void update_savesAllFieldsFromRequest() {
            when(repo.findById(orgId)).thenReturn(Optional.empty());
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateSettingsRequest req = new UpdateSettingsRequest(
                    95.0, 85.0, 300.0, 60, 120, true, false, true);

            PlatformSettings result = service.update(orgId, req, "admin");

            assertThat(result.getTemperatureThreshold()).as("temperature threshold").isEqualTo(95.0);
            assertThat(result.getHumidityThreshold()).as("humidity threshold").isEqualTo(85.0);
            assertThat(result.getSmokeThreshold()).as("smoke threshold").isEqualTo(300.0);
            assertThat(result.getTelemetryRetentionDays()).as("telemetry retention days").isEqualTo(60);
            assertThat(result.getAuditRetentionDays()).as("audit retention days").isEqualTo(120);
            assertThat(result.isSlackEnabled()).as("slack enabled").isTrue();
            assertThat(result.isLineEnabled()).as("line disabled").isFalse();
            assertThat(result.isWebhookEnabled()).as("webhook enabled").isTrue();
            assertThat(result.getUpdatedBy()).as("updatedBy").isEqualTo("admin");
            assertThat(result.getUpdatedAt()).as("updatedAt timestamp set").isNotNull();
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("merges request values into an existing settings record when one is found")
        void update_updatesExistingSettings_whenFound() {
            PlatformSettings existing = new PlatformSettings();
            existing.setOrganizationId(orgId);
            existing.setTemperatureThreshold(80.0);
            when(repo.findById(orgId)).thenReturn(Optional.of(existing));
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateSettingsRequest req = new UpdateSettingsRequest(
                    55.0, 70.0, 150.0, 14, 30, null, null, null);

            PlatformSettings result = service.update(orgId, req, "operator");

            assertThat(result.getTemperatureThreshold()).as("updated temperature threshold").isEqualTo(55.0);
            assertThat(result.getUpdatedBy()).as("updatedBy").isEqualTo("operator");
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("null notification flags in the request do not overwrite existing enabled flags")
        void update_nullNotificationFlags_doNotOverwriteExistingValues() {
            PlatformSettings existing = new PlatformSettings();
            existing.setOrganizationId(orgId);
            existing.setSlackEnabled(true);
            existing.setLineEnabled(true);
            existing.setWebhookEnabled(true);
            when(repo.findById(orgId)).thenReturn(Optional.of(existing));
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateSettingsRequest req = new UpdateSettingsRequest(
                    80.0, 90.0, 200.0, 30, 90, null, null, null);

            PlatformSettings result = service.update(orgId, req, "admin");

            assertThat(result.isSlackEnabled()).as("slack must remain enabled").isTrue();
            assertThat(result.isLineEnabled()).as("line must remain enabled").isTrue();
            assertThat(result.isWebhookEnabled()).as("webhook must remain enabled").isTrue();
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("sets the organisationId on a newly created settings record")
        void update_setsOrganizationId_onNewSettings() {
            when(repo.findById(orgId)).thenReturn(Optional.empty());
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateSettingsRequest req = new UpdateSettingsRequest(
                    80.0, 90.0, 200.0, 30, 90, false, false, false);

            PlatformSettings result = service.update(orgId, req, "admin");

            assertThat(result.getOrganizationId()).as("organizationId on new record").isEqualTo(orgId);
        }
    }
}
