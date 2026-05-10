package com.sentinel.iot.controller;

import com.sentinel.iot.dto.DeviceRequest;
import com.sentinel.iot.model.Device;
import com.sentinel.iot.service.DeviceService;
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
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping
    public ResponseEntity<Device> create(@Valid @RequestBody DeviceRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deviceService.create(req));
    }

    @GetMapping
    public ResponseEntity<List<Device>> findAll() {
        return ResponseEntity.ok(deviceService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Device> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(deviceService.findById(id));
    }
}
