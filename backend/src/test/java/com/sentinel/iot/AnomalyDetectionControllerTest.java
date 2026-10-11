package com.sentinel.iot;

import com.sentinel.iot.anomaly.dto.AdaptiveThresholdConfig;
import com.sentinel.iot.anomaly.dto.AnomalyPredictionResult;
import com.sentinel.iot.anomaly.dto.DynamicBaseline;
import com.sentinel.iot.anomaly.dto.TelemetryAnomalyEvaluationRequest;
import com.sentinel.iot.anomaly.service.AdaptiveThresholdService;
import com.sentinel.iot.anomaly.service.StreamingAnomalyInferenceService;
import com.sentinel.iot.controller.AnomalyDetectionController;
import com.sentinel.iot.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("AnomalyDetectionController (Phase 4)")
@ExtendWith(MockitoExtension.class)
class AnomalyDetectionControllerTest {

    @Mock
    private StreamingAnomalyInferenceService inferenceService;

    @Mock
    private AdaptiveThresholdService thresholdService;

    @InjectMocks
    private AnomalyDetectionController controller;

    private final UUID testOrg = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final UUID deviceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(testOrg);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("POST /evaluate performs dry-run inference successfully")
    void evaluateTelemetrySuccess() {
        TelemetryAnomalyEvaluationRequest request = TelemetryAnomalyEvaluationRequest.builder()
                .deviceId(deviceId)
                .temperature(85.0)
                .humidity(12.0)
                .smokePpm(75.0)
                .build();

        AnomalyPredictionResult mockResult = AnomalyPredictionResult.builder()
                .deviceId(deviceId)
                .anomaly(true)
                .anomalyScore(0.92)
                .anomalyType("THERMAL_RUNAWAY")
                .severity("CRITICAL")
                .build();

        when(inferenceService.evaluateDryRun(eq(testOrg), eq(request))).thenReturn(mockResult);

        ResponseEntity<AnomalyPredictionResult> response = controller.evaluateTelemetry(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isAnomaly()).isTrue();
        assertThat(response.getBody().getAnomalyType()).isEqualTo("THERMAL_RUNAWAY");
        assertThat(response.getBody().getSeverity()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("GET /baselines/{deviceId} returns device sensor dynamic baseline bands")
    void getDeviceBaselinesSuccess() {
        DynamicBaseline tempBase = DynamicBaseline.builder()
                .sensorKey("TEMPERATURE").mean(26.4).stdDev(2.1).lowerBand(20.1).upperBand(32.7)
                .sampleCount(45).build();

        when(thresholdService.getBaselinesForDevice(eq(testOrg), eq(deviceId)))
                .thenReturn(Map.of("TEMPERATURE", tempBase));

        ResponseEntity<Map<String, DynamicBaseline>> response = controller.getDeviceBaselines(deviceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("TEMPERATURE");
        assertThat(response.getBody().get("TEMPERATURE").getMean()).isEqualTo(26.4);
    }

    @Test
    @DisplayName("POST /baselines/{deviceId}/reset resets all baselines for device")
    void resetDeviceBaselinesSuccess() {
        ResponseEntity<Map<String, String>> response = controller.resetDeviceBaselines(deviceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("SUCCESS");
        assertThat(response.getBody().get("deviceId")).isEqualTo(deviceId.toString());

        verify(thresholdService).resetBaselines(eq(testOrg), eq(deviceId));
    }

    @Test
    @DisplayName("DELETE /baselines/{deviceId}/{sensorKey} resets single sensor baseline")
    void resetSensorBaselineSuccess() {
        ResponseEntity<Map<String, String>> response = controller.resetSensorBaseline(deviceId, "TEMPERATURE");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("SUCCESS");
        assertThat(response.getBody().get("sensorKey")).isEqualTo("TEMPERATURE");

        verify(thresholdService).resetBaseline(eq(testOrg), eq(deviceId), eq("TEMPERATURE"));
    }

    @Test
    @DisplayName("GET /config returns active adaptive threshold configuration")
    void getConfigSuccess() {
        ResponseEntity<AdaptiveThresholdConfig> response = controller.getConfig();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAlpha()).isEqualTo(0.05);
        assertThat(response.getBody().getSigmaMultiplier()).isEqualTo(3.0);
        assertThat(response.getBody().isEnabled()).isTrue();
    }
}
