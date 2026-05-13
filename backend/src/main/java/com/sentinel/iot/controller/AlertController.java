package com.sentinel.iot.controller;

import com.sentinel.iot.model.Alert;
import com.sentinel.iot.service.AlertService;
import com.sentinel.iot.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts", description = "Threshold alert retrieval and acknowledgment")
public class AlertController {

    private final AlertService alertService;
    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "Get paginated alerts ordered by most recent")
    public ResponseEntity<Page<Alert>> getAlerts(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(alertService.getPage(page, size));
    }

    @GetMapping("/unacknowledged")
    @Operation(summary = "Get all unacknowledged alerts")
    public ResponseEntity<List<Alert>> getUnacknowledged() {
        return ResponseEntity.ok(alertService.getUnacknowledged());
    }

    @GetMapping("/device/{deviceId}")
    @Operation(summary = "Get all alerts for a specific device")
    public ResponseEntity<List<Alert>> getByDevice(@PathVariable UUID deviceId) {
        return ResponseEntity.ok(alertService.getByDevice(deviceId));
    }

    @PutMapping("/acknowledge-all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Acknowledge all unacknowledged alerts (ADMIN only)")
    public ResponseEntity<Void> acknowledgeAll(Authentication authentication,
                                               HttpServletRequest request) {
        int count = alertService.acknowledgeAll();
        String ip = resolveIp(request);
        auditService.log(authentication.getName(), "ACKNOWLEDGE_ALL_ALERTS",
                "/api/alerts/acknowledge-all", "count=" + count, ip);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/acknowledge")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Acknowledge an alert (ADMIN only)")
    public ResponseEntity<Void> acknowledge(@PathVariable UUID id,
                                            Authentication authentication,
                                            HttpServletRequest request) {
        alertService.acknowledge(id);
        String ip = resolveIp(request);
        auditService.log(authentication.getName(), "ACKNOWLEDGE_ALERT",
                "/api/alerts/" + id + "/acknowledge", "alertId=" + id, ip);
        return ResponseEntity.noContent().build();
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
