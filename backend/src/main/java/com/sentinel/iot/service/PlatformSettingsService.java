package com.sentinel.iot.service;

import com.sentinel.iot.dto.UpdateSettingsRequest;
import com.sentinel.iot.model.PlatformSettings;
import com.sentinel.iot.repository.PlatformSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    private final PlatformSettingsRepository repo;

    @Value("${alert.temperature-threshold:80.0}") private double defaultTemp;
    @Value("${alert.humidity-threshold:90.0}")    private double defaultHum;
    @Value("${alert.smoke-threshold:200.0}")       private double defaultSmoke;
    @Value("${telemetry.retention-days:30}")       private int    defaultTelDays;
    @Value("${audit.retention-days:90}")           private int    defaultAuditDays;

    @Transactional(readOnly = true)
    public PlatformSettings getOrDefault(UUID orgId) {
        return repo.findById(Objects.requireNonNull(orgId)).orElseGet(() -> buildDefault(orgId));
    }

    @Transactional
    public PlatformSettings update(UUID orgId, UpdateSettingsRequest req, String updatedBy) {
        PlatformSettings s = repo.findById(Objects.requireNonNull(orgId)).orElseGet(() -> buildDefault(orgId));
        s.setOrganizationId(orgId);
        s.setTemperatureThreshold(req.temperatureCelsius());
        s.setHumidityThreshold(req.humidityPercent());
        s.setSmokeThreshold(req.smokePpm());
        s.setTelemetryRetentionDays(req.telemetryDays());
        s.setAuditRetentionDays(req.auditDays());
        if (req.slack()   != null) s.setSlackEnabled(req.slack());
        if (req.line()    != null) s.setLineEnabled(req.line());
        if (req.webhook() != null) s.setWebhookEnabled(req.webhook());
        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(updatedBy);
        return repo.save(s);
    }

    private PlatformSettings buildDefault(UUID orgId) {
        PlatformSettings s = new PlatformSettings();
        s.setOrganizationId(orgId);
        s.setTemperatureThreshold(defaultTemp);
        s.setHumidityThreshold(defaultHum);
        s.setSmokeThreshold(defaultSmoke);
        s.setTelemetryRetentionDays(defaultTelDays);
        s.setAuditRetentionDays(defaultAuditDays);
        s.setSlackEnabled(false);
        s.setLineEnabled(false);
        s.setWebhookEnabled(false);
        return s;
    }
}
