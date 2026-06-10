package com.sentinel.iot;

import com.sentinel.iot.dto.UpdateSettingsRequest;
import com.sentinel.iot.model.PlatformSettings;
import com.sentinel.iot.repository.PlatformSettingsRepository;
import com.sentinel.iot.service.PlatformSettingsService;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.*;

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

    // ---- getOrDefault --------------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void getOrDefault_returnsExistingSettings_whenFound() {
        PlatformSettings existing = new PlatformSettings();
        existing.setOrganizationId(orgId);
        existing.setTemperatureThreshold(75.0);
        when(repo.findById(orgId)).thenReturn(Optional.of(existing));

        PlatformSettings result = service.getOrDefault(orgId);

        assertThat(result).isSameAs(existing);
        assertThat(result.getTemperatureThreshold()).isEqualTo(75.0);
    }

    @SuppressWarnings("null")
    @Test
    void getOrDefault_returnsDefaultValues_whenSettingsNotFound() {
        when(repo.findById(orgId)).thenReturn(Optional.empty());

        PlatformSettings result = service.getOrDefault(orgId);

        assertThat(result.getOrganizationId()).isEqualTo(orgId);
        assertThat(result.getTemperatureThreshold()).isEqualTo(80.0);
        assertThat(result.getHumidityThreshold()).isEqualTo(90.0);
        assertThat(result.getSmokeThreshold()).isEqualTo(200.0);
        assertThat(result.getTelemetryRetentionDays()).isEqualTo(30);
        assertThat(result.getAuditRetentionDays()).isEqualTo(90);
        assertThat(result.isSlackEnabled()).isFalse();
        assertThat(result.isLineEnabled()).isFalse();
        assertThat(result.isWebhookEnabled()).isFalse();
    }

    // ---- update --------------------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void update_savesAllFieldsFromRequest() {
        when(repo.findById(orgId)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateSettingsRequest req = new UpdateSettingsRequest(
                95.0, 85.0, 300.0, 60, 120, true, false, true);

        PlatformSettings result = service.update(orgId, req, "admin");

        assertThat(result.getTemperatureThreshold()).isEqualTo(95.0);
        assertThat(result.getHumidityThreshold()).isEqualTo(85.0);
        assertThat(result.getSmokeThreshold()).isEqualTo(300.0);
        assertThat(result.getTelemetryRetentionDays()).isEqualTo(60);
        assertThat(result.getAuditRetentionDays()).isEqualTo(120);
        assertThat(result.isSlackEnabled()).isTrue();
        assertThat(result.isLineEnabled()).isFalse();
        assertThat(result.isWebhookEnabled()).isTrue();
        assertThat(result.getUpdatedBy()).isEqualTo("admin");
        assertThat(result.getUpdatedAt()).isNotNull();
    }

    @SuppressWarnings("null")
    @Test
    void update_updatesExistingSettings_whenFound() {
        PlatformSettings existing = new PlatformSettings();
        existing.setOrganizationId(orgId);
        existing.setTemperatureThreshold(80.0);
        when(repo.findById(orgId)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateSettingsRequest req = new UpdateSettingsRequest(
                55.0, 70.0, 150.0, 14, 30, null, null, null);

        PlatformSettings result = service.update(orgId, req, "operator");

        assertThat(result.getTemperatureThreshold()).isEqualTo(55.0);
        assertThat(result.getUpdatedBy()).isEqualTo("operator");
    }

    @SuppressWarnings("null")
    @Test
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

        assertThat(result.isSlackEnabled()).isTrue();
        assertThat(result.isLineEnabled()).isTrue();
        assertThat(result.isWebhookEnabled()).isTrue();
    }

    @SuppressWarnings("null")
    @Test
    void update_setsOrganizationId_onNewSettings() {
        when(repo.findById(orgId)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateSettingsRequest req = new UpdateSettingsRequest(
                80.0, 90.0, 200.0, 30, 90, false, false, false);

        PlatformSettings result = service.update(orgId, req, "admin");

        assertThat(result.getOrganizationId()).isEqualTo(orgId);
    }
}
