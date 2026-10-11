package com.sentinel.iot.controller;

import com.sentinel.iot.anomaly.dto.AdaptiveThresholdConfig;
import com.sentinel.iot.anomaly.dto.AnomalyPredictionResult;
import com.sentinel.iot.anomaly.dto.DynamicBaseline;
import com.sentinel.iot.anomaly.dto.TelemetryAnomalyEvaluationRequest;
import com.sentinel.iot.anomaly.service.AdaptiveThresholdService;
import com.sentinel.iot.anomaly.service.StreamingAnomalyInferenceService;
import com.sentinel.iot.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST API for Realtime AI & Predictive Anomaly Detection (RFC-001 Phase 4).
 * Provides endpoints for dry-run anomaly scoring, adaptive dynamic threshold inspection,
 * and baseline lifecycle operations.
 */
@RestController
@RequestMapping("/api/v1/analytics/anomalies")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Predictive Anomaly Detection", description = "Realtime ML Inference, Isolation Forest and Adaptive Dynamic Thresholds")
public class AnomalyDetectionController {

    private final StreamingAnomalyInferenceService inferenceService;
    private final AdaptiveThresholdService thresholdService;

    @PostMapping("/evaluate")
    @Operation(summary = "Dry-run ML Anomaly Scoring",
               description = "Performs multivariate Isolation Forest scoring and adaptive baseline evaluation on-the-fly without creating database alerts.")
    public ResponseEntity<AnomalyPredictionResult> evaluateTelemetry(
            @RequestBody TelemetryAnomalyEvaluationRequest request) {
        UUID orgId = TenantContext.get();
        AnomalyPredictionResult result = inferenceService.evaluateDryRun(orgId, request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/baselines/{deviceId}")
    @Operation(summary = "Get Dynamic Baselines for Device",
               description = "Retrieves rolling statistical baseline bands (μ ± kσ) for all sensors of the specified device.")
    public ResponseEntity<Map<String, DynamicBaseline>> getDeviceBaselines(
            @PathVariable UUID deviceId) {
        UUID orgId = TenantContext.get();
        Map<String, DynamicBaseline> baselines = thresholdService.getBaselinesForDevice(orgId, deviceId);
        return ResponseEntity.ok(baselines);
    }

    @PostMapping("/baselines/{deviceId}/reset")
    @Operation(summary = "Reset Dynamic Baselines for Device",
               description = "Resets rolling statistical baselines for all sensors on the device (e.g. after maintenance or calibration).")
    public ResponseEntity<Map<String, String>> resetDeviceBaselines(
            @PathVariable UUID deviceId) {
        UUID orgId = TenantContext.get();
        thresholdService.resetBaselines(orgId, deviceId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "deviceId", deviceId.toString(),
                "message", "Dynamic baselines successfully reset"
        ));
    }

    @DeleteMapping("/baselines/{deviceId}/{sensorKey}")
    @Operation(summary = "Reset Dynamic Baseline for a Specific Sensor",
               description = "Resets rolling statistical baseline for a single sensor on the device.")
    public ResponseEntity<Map<String, String>> resetSensorBaseline(
            @PathVariable UUID deviceId,
            @PathVariable String sensorKey) {
        UUID orgId = TenantContext.get();
        thresholdService.resetBaseline(orgId, deviceId, sensorKey);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "deviceId", deviceId.toString(),
                "sensorKey", sensorKey,
                "message", "Sensor baseline successfully reset"
        ));
    }

    @GetMapping("/config")
    @Operation(summary = "Get Adaptive Threshold Configuration",
               description = "Returns current default parameters for the adaptive threshold learning rate and sigma multipliers.")
    public ResponseEntity<AdaptiveThresholdConfig> getConfig() {
        return ResponseEntity.ok(AdaptiveThresholdConfig.defaults());
    }
}
