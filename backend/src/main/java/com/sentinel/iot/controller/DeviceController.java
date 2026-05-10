package com.sentinel.iot.controller;

import com.sentinel.iot.dto.DeviceLifecycleRequest;
import com.sentinel.iot.dto.DeviceRequest;
import com.sentinel.iot.dto.FirmwareUpdateRequest;
import com.sentinel.iot.model.Device;
import com.sentinel.iot.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Tag(name = "Devices", description = "Device registration, lifecycle, and firmware management")
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping
    @Operation(summary = "Register a new device (ADMIN only)")
    public ResponseEntity<Device> create(@Valid @RequestBody DeviceRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deviceService.create(req));
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
            @Valid @RequestBody DeviceLifecycleRequest req) {
        return ResponseEntity.ok(deviceService.updateLifecycle(id, req));
    }

    @PatchMapping("/{id}/firmware")
    @Operation(summary = "Record a firmware version update on the device (ADMIN only)")
    public ResponseEntity<Device> updateFirmware(
            @PathVariable UUID id,
            @Valid @RequestBody FirmwareUpdateRequest req) {
        return ResponseEntity.ok(deviceService.updateFirmware(id, req));
    }
}
