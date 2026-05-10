package com.sentinel.iot.controller;

import com.sentinel.iot.model.Telemetry;
import com.sentinel.iot.service.RedisService;
import com.sentinel.iot.service.TelemetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final TelemetryService telemetryService;
    private final RedisService redisService;

    @GetMapping("/{deviceId}/latest")
    public ResponseEntity<List<Telemetry>> getLatest(
            @PathVariable UUID deviceId,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(telemetryService.getLatest(deviceId, Math.min(limit, 200)));
    }

    @GetMapping("/{deviceId}/cache")
    public ResponseEntity<Map<Object, Object>> getCached(@PathVariable UUID deviceId) {
        return ResponseEntity.ok(redisService.getLatestTelemetry(deviceId.toString()));
    }

    @GetMapping("/{deviceId}/range")
    public ResponseEntity<List<Telemetry>> getRange(
            @PathVariable UUID deviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(telemetryService.getRange(deviceId, from, to));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> stats() {
        return ResponseEntity.ok(Map.of("lastMinute", telemetryService.countLastMinute()));
    }
}
