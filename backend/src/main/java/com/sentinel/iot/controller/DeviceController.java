package com.sentinel.iot.controller;

import com.sentinel.iot.dto.DeviceLifecycleRequest;
import com.sentinel.iot.dto.DeviceRequest;
import com.sentinel.iot.dto.FirmwareUpdateRequest;
import com.sentinel.iot.model.Device;
import com.sentinel.iot.service.AuditService;
import com.sentinel.iot.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Tag(name = "Devices", description = "Device registration, lifecycle, and firmware management")
public class DeviceController {

    private final DeviceService deviceService;
    private final AuditService auditService;

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

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
