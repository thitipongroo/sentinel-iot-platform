package com.sentinel.iot.controller;

import com.sentinel.iot.dto.UpdateSettingsRequest;
import com.sentinel.iot.model.PlatformSettings;
import com.sentinel.iot.security.TenantContext;
import com.sentinel.iot.service.AuditService;
import com.sentinel.iot.service.PlatformSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "Platform configuration — readable by all, writable by ADMIN")
public class SettingsController {

    private final PlatformSettingsService settingsService;
    private final AuditService            auditService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get current platform settings (thresholds, retention, notifications)")
    public ResponseEntity<Map<String, Object>> getSettings() {
        return ResponseEntity.ok(toMap(settingsService.getOrDefault(TenantContext.get())));
    }

    @PatchMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update platform settings — ADMIN only")
    public ResponseEntity<Map<String, Object>> updateSettings(
            @Valid @RequestBody UpdateSettingsRequest req,
            Authentication auth,
            HttpServletRequest httpRequest) {
        PlatformSettings updated = settingsService.update(TenantContext.get(), req, auth.getName());
        auditService.log(auth.getName(), "SETTINGS_UPDATE", "/api/v1/settings",
                null, resolveIp(httpRequest));
        return ResponseEntity.ok(toMap(updated));
    }

    private Map<String, Object> toMap(PlatformSettings s) {
        return Map.of(
                "thresholds", Map.of(
                        "temperatureCelsius", s.getTemperatureThreshold(),
                        "humidityPercent",    s.getHumidityThreshold(),
                        "smokePpm",           s.getSmokeThreshold()
                ),
                "retention", Map.of(
                        "telemetryDays", s.getTelemetryRetentionDays(),
                        "auditDays",     s.getAuditRetentionDays()
                ),
                "notifications", Map.of(
                        "slack",   s.isSlackEnabled(),
                        "line",    s.isLineEnabled(),
                        "webhook", s.isWebhookEnabled()
                )
        );
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
