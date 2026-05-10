package com.sentinel.iot.controller;

import com.sentinel.iot.model.Telemetry;
import com.sentinel.iot.model.TelemetryHourlyAggregate;
import com.sentinel.iot.service.DeviceService;
import com.sentinel.iot.service.RedisService;
import com.sentinel.iot.service.TelemetryRetentionService;
import com.sentinel.iot.service.TelemetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Telemetry", description = "Sensor telemetry retrieval")
public class TelemetryController {

    private final TelemetryService telemetryService;
    private final TelemetryRetentionService retentionService;
    private final RedisService redisService;
    private final DeviceService deviceService;

    @GetMapping("/{deviceId}/latest")
    @Operation(summary = "Get latest N telemetry readings from PostgreSQL (max 200)")
    public ResponseEntity<List<Telemetry>> getLatest(
            @PathVariable UUID deviceId,
            @RequestParam(defaultValue = "50") int limit) {
        deviceService.findById(deviceId); // ownership check — throws 404 if device not in caller's org
        return ResponseEntity.ok(telemetryService.getLatest(deviceId, Math.min(limit, 200)));
    }

    @GetMapping("/{deviceId}/cache")
    @Operation(summary = "Get the most recent telemetry reading from Redis (sub-millisecond)")
    public ResponseEntity<Map<Object, Object>> getCached(@PathVariable UUID deviceId) {
        deviceService.findById(deviceId); // ownership check
        return ResponseEntity.ok(redisService.getLatestTelemetry(deviceId.toString()));
    }

    @GetMapping("/{deviceId}/range")
    @Operation(summary = "Get raw telemetry within a time range")
    public ResponseEntity<List<Telemetry>> getRange(
            @PathVariable UUID deviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        deviceService.findById(deviceId); // ownership check
        return ResponseEntity.ok(telemetryService.getRange(deviceId, from, to));
    }

    @GetMapping("/{deviceId}/hourly")
    @Operation(summary = "Get hourly aggregated telemetry for historical analytics (persists beyond retention window)")
    public ResponseEntity<List<TelemetryHourlyAggregate>> getHourly(
            @PathVariable UUID deviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        deviceService.findById(deviceId); // ownership check
        return ResponseEntity.ok(retentionService.getHourlyAggregates(deviceId, from, to));
    }

    @GetMapping("/stats")
    @Operation(summary = "Count telemetry events received in the last 60 seconds and replay queue depth")
    public ResponseEntity<Map<String, Long>> stats() {
        return ResponseEntity.ok(Map.of(
                "lastMinute",      telemetryService.countLastMinute(),
                "replayQueueSize", redisService.replayQueueSize()
        ));
    }
}
