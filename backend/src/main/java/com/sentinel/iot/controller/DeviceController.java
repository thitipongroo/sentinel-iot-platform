package com.sentinel.iot.controller;

import com.sentinel.iot.dto.DeviceCapabilityRequest;
import com.sentinel.iot.dto.DeviceEnrollRequest;
import com.sentinel.iot.dto.DeviceLifecycleRequest;
import com.sentinel.iot.dto.DeviceRequest;
import com.sentinel.iot.dto.EnrollmentTokenResponse;
import com.sentinel.iot.dto.FirmwareUpdateRequest;
import com.sentinel.iot.model.Device;
import com.sentinel.iot.model.SensorCapability;
import com.sentinel.iot.security.TenantContext;
import com.sentinel.iot.service.AuditService;
import com.sentinel.iot.service.DeviceEnrollmentService;
import com.sentinel.iot.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Tag(name = "Devices", description = "Device registration, lifecycle, firmware, and capability management")
public class DeviceController {

    private final DeviceService deviceService;
    private final AuditService auditService;
    private final DeviceEnrollmentService enrollmentService;

    @PostMapping
    @Operation(summary = "Register a new device (ADMIN only)")
    public ResponseEntity<Device> create(@Valid @RequestBody DeviceRequest req,
                                         Authentication authentication,
                                         HttpServletRequest httpRequest) {
        Device created = deviceService.create(req);
        auditService.log(authentication.getName(), "DEVICE_CREATE",
                "/api/devices", "name=" + req.getName(), resolveIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "List all registered devices with live status from Redis")
    public ResponseEntity<List<Device>> findAll() {
        return ResponseEntity.ok(deviceService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get device by ID")
    public ResponseEntity<Device> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(deviceService.findById(id));
    }

    @PatchMapping("/{id}/lifecycle")
    @Operation(summary = "Transition device lifecycle state (ADMIN only). " +
               "DECOMMISSIONED is terminal — no further transitions allowed.")
    public ResponseEntity<Device> updateLifecycle(
            @PathVariable UUID id,
            @Valid @RequestBody DeviceLifecycleRequest req,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        Device updated = deviceService.updateLifecycle(id, req);
        auditService.log(authentication.getName(), "DEVICE_LIFECYCLE_UPDATE",
                "/api/devices/" + id + "/lifecycle",
                "status=" + req.getLifecycleStatus(), resolveIp(httpRequest));
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/firmware")
    @Operation(summary = "Record a firmware version update on the device (ADMIN only)")
    public ResponseEntity<Device> updateFirmware(
            @PathVariable UUID id,
            @Valid @RequestBody FirmwareUpdateRequest req,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        Device updated = deviceService.updateFirmware(id, req);
        auditService.log(authentication.getName(), "DEVICE_FIRMWARE_UPDATE",
                "/api/devices/" + id + "/firmware",
                "version=" + req.getFirmwareVersion(), resolveIp(httpRequest));
        return ResponseEntity.ok(updated);
    }

    // ── Device enrollment ─────────────────────────────────────────────────────

    @PostMapping("/{id}/enrollment-token")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Generate a one-time enrollment token for a device (ADMIN only)",
               description = "Token is valid for a configurable TTL (default 24 h) and can only be " +
                             "used once. The raw token is returned once — store it securely and deliver " +
                             "it to the device over a secure out-of-band channel.")
    public ResponseEntity<EnrollmentTokenResponse> generateEnrollmentToken(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID orgId = TenantContext.get();
        EnrollmentTokenResponse response = enrollmentService.generateToken(id, orgId, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/enroll")
    @Operation(summary = "Enroll a device using a one-time token (called by the device itself)",
               description = "Unauthenticated endpoint — the enrollment token is the credential. " +
                             "On success the device transitions to ACTIVE and receives its MQTT credentials.")
    public ResponseEntity<Map<String, String>> enroll(
            @RequestBody DeviceEnrollRequest request,
            HttpServletRequest httpRequest) {
        String remoteIp = resolveIp(httpRequest);
        String mqttPassword = enrollmentService.enroll(request, remoteIp);
        return ResponseEntity.ok(Map.of(
                "mqttUsername", "device-" + request.deviceId(),
                "mqttPassword", mqttPassword
        ));
    }

    // ── Sensor capability management ──────────────────────────────────────────

    @GetMapping("/{id}/capabilities")
    @Operation(summary = "Get the sensor capability map for a device",
               description = "Returns the declared sensors, their units, and per-device alert " +
                             "thresholds. Null response means no capabilities declared; " +
                             "global application thresholds apply.")
    public ResponseEntity<Map<String, SensorCapability>> getCapabilities(@PathVariable UUID id) {
        Map<String, SensorCapability> caps = deviceService.getCapabilities(id);
        return ResponseEntity.ok(caps);
    }

    @PutMapping("/{id}/capabilities")
    @Operation(summary = "Replace the sensor capability map for a device (ADMIN only)",
               description = "Sends the full desired capability state. The alert engine will " +
                             "immediately start using per-device thresholds for any subsequent " +
                             "telemetry from this device. Send an empty map to revert to global thresholds.")
    public ResponseEntity<Device> updateCapabilities(
            @PathVariable UUID id,
            @Valid @RequestBody DeviceCapabilityRequest req,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        Device updated = deviceService.updateCapabilities(id, req);
        auditService.log(authentication.getName(), "DEVICE_CAPABILITY_UPDATE",
                "/api/devices/" + id + "/capabilities",
                "sensors=" + req.capabilities().keySet(), resolveIp(httpRequest));
        return ResponseEntity.ok(updated);
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
