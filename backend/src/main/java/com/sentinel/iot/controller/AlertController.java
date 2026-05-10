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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts", description = "Threshold alert retrieval and acknowledgment")
public class AlertController {

    private final AlertService alertService;
    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "Get the 50 most recent alerts")
    public ResponseEntity<List<Alert>> getRecent() {
        return ResponseEntity.ok(alertService.getRecent());
    }

    @GetMapping("/unacknowledged")
    @Operation(summary = "Get all unacknowledged alerts")
    public ResponseEntity<List<Alert>> getUnacknowledged() {
        return ResponseEntity.ok(alertService.getUnacknowledged());
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
