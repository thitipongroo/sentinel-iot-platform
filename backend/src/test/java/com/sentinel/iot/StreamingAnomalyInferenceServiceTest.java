package com.sentinel.iot;

import com.sentinel.iot.anomaly.dto.AnomalyPredictionResult;
import com.sentinel.iot.anomaly.dto.DynamicBaseline;
import com.sentinel.iot.anomaly.dto.TelemetryAnomalyEvaluationRequest;
import com.sentinel.iot.anomaly.engine.MultivariateAnomalyDetector;
import com.sentinel.iot.anomaly.service.AdaptiveThresholdService;
import com.sentinel.iot.anomaly.service.StreamingAnomalyInferenceService;
import com.sentinel.iot.service.AlertService;
import com.sentinel.iot.service.NotificationService;
import com.sentinel.iot.service.RedisService;
import com.sentinel.iot.service.notification.AlertDeduplicator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("StreamingAnomalyInferenceService (Phase 4)")
@ExtendWith(MockitoExtension.class)
class StreamingAnomalyInferenceServiceTest {

    private MultivariateAnomalyDetector detector;

    @Mock
    private AdaptiveThresholdService thresholdService;

    @Mock
    private AlertService alertService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AlertDeduplicator alertDeduplicator;

    @Mock
    private RedisService redisService;

    private StreamingAnomalyInferenceService inferenceService;

    private final UUID orgId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private final UUID deviceId = UUID.fromString("dddddddd-0000-0000-0000-000000000001");
    private final String deviceName = "HVAC-Unit-01";

    @BeforeEach
    void setUp() {
        detector = new MultivariateAnomalyDetector(42L, 64);
        inferenceService = new StreamingAnomalyInferenceService(
                detector, thresholdService, alertService,
                notificationService, alertDeduplicator, redisService,
                new SimpleMeterRegistry()
        );
    }

    @Nested
    @DisplayName("Streaming Telemetry Evaluation")
    class StreamingEvaluation {

        @Test
        @DisplayName("Nominal telemetry yields no alert and returns nominal prediction result")
        void nominalTelemetryProducesNoAlert() {
            when(redisService.getLatestTelemetry(anyString())).thenReturn(Collections.emptyMap());

            DynamicBaseline tempBase = DynamicBaseline.builder()
                    .sensorKey("TEMPERATURE").mean(25.0).stdDev(2.0).lowerBand(19.0).upperBand(31.0)
                    .sampleCount(20).lastObservedValue(25.5).zScore(0.25).breached(false).build();
            DynamicBaseline humBase = DynamicBaseline.builder()
                    .sensorKey("HUMIDITY").mean(50.0).stdDev(5.0).lowerBand(35.0).upperBand(65.0)
                    .sampleCount(20).lastObservedValue(51.0).zScore(0.2).breached(false).build();
            DynamicBaseline smokeBase = DynamicBaseline.builder()
                    .sensorKey("SMOKE_PPM").mean(1.0).stdDev(0.5).lowerBand(0.0).upperBand(2.5)
                    .sampleCount(20).lastObservedValue(1.1).zScore(0.2).breached(false).build();

            when(thresholdService.updateAndEvaluate(eq(orgId), eq(deviceId), eq("TEMPERATURE"), anyDouble())).thenReturn(tempBase);
            when(thresholdService.updateAndEvaluate(eq(orgId), eq(deviceId), eq("HUMIDITY"), anyDouble())).thenReturn(humBase);
            when(thresholdService.updateAndEvaluate(eq(orgId), eq(deviceId), eq("SMOKE_PPM"), anyDouble())).thenReturn(smokeBase);

            AnomalyPredictionResult result = inferenceService.processTelemetry(
                    deviceId, orgId, deviceName, 25.5, 51.0, 1.1, false, null
            );

            assertFalse(result.isAnomaly());
            assertEquals("NONE", result.anomalyType());
            assertEquals("INFO", result.getSeverity());
            assertTrue(result.getAnomalyScore() < 0.50);

            // No alert fired
            verify(alertService, never()).createAlert(any(), any(), any(), any());
            verify(notificationService, never()).send(anyString());
        }

        @Test
        @DisplayName("Thermal runaway pattern fires CRITICAL alert and triggers notification")
        void thermalRunawayFiresCriticalAlert() {
            when(redisService.getLatestTelemetry(anyString())).thenReturn(Map.of(
                    "temperature", "30.0",
                    "humidity", "40.0"
            ));

            DynamicBaseline tempBase = DynamicBaseline.builder()
                    .sensorKey("TEMPERATURE").mean(25.0).stdDev(3.0).lowerBand(16.0).upperBand(34.0)
                    .sampleCount(50).lastObservedValue(85.0).zScore(20.0).breached(true).build();
            DynamicBaseline humBase = DynamicBaseline.builder()
                    .sensorKey("HUMIDITY").mean(50.0).stdDev(5.0).lowerBand(35.0).upperBand(65.0)
                    .sampleCount(50).lastObservedValue(10.0).zScore(-8.0).breached(true).build();
            DynamicBaseline smokeBase = DynamicBaseline.builder()
                    .sensorKey("SMOKE_PPM").mean(1.0).stdDev(1.0).lowerBand(0.0).upperBand(4.0)
                    .sampleCount(50).lastObservedValue(80.0).zScore(79.0).breached(true).build();

            when(thresholdService.updateAndEvaluate(eq(orgId), eq(deviceId), eq("TEMPERATURE"), anyDouble())).thenReturn(tempBase);
            when(thresholdService.updateAndEvaluate(eq(orgId), eq(deviceId), eq("HUMIDITY"), anyDouble())).thenReturn(humBase);
            when(thresholdService.updateAndEvaluate(eq(orgId), eq(deviceId), eq("SMOKE_PPM"), anyDouble())).thenReturn(smokeBase);

            when(alertDeduplicator.shouldSend(eq(deviceId), eq("PREDICTIVE_AI_THERMAL_RUNAWAY"), eq("CRITICAL"))).thenReturn(true);

            AnomalyPredictionResult result = inferenceService.processTelemetry(
                    deviceId, orgId, deviceName, 85.0, 10.0, 80.0, false, null
            );

            assertTrue(result.isAnomaly());
            assertEquals("THERMAL_RUNAWAY", result.anomalyType());
            assertEquals("CRITICAL", result.getSeverity());
            assertTrue(result.getAnomalyScore() >= 0.88);
            assertTrue(result.getConfidence() >= 0.95);

            // Alert created
            verify(alertService).createAlert(eq(deviceId), eq("CRITICAL"), contains("THERMAL_RUNAWAY"), eq(orgId));
            // Notification sent
            verify(notificationService).send(contains("THERMAL_RUNAWAY"));
        }

        @Test
        @DisplayName("Suppresses duplicate notifications when AlertDeduplicator returns false")
        void suppressesDuplicateNotifications() {
            when(redisService.getLatestTelemetry(anyString())).thenReturn(Collections.emptyMap());

            DynamicBaseline tempBase = DynamicBaseline.builder()
                    .sensorKey("TEMPERATURE").mean(25.0).stdDev(2.0).lowerBand(19.0).upperBand(31.0)
                    .sampleCount(50).lastObservedValue(90.0).zScore(32.5).breached(true).build();
            DynamicBaseline humBase = DynamicBaseline.builder()
                    .sensorKey("HUMIDITY").mean(50.0).stdDev(5.0).lowerBand(35.0).upperBand(65.0)
                    .sampleCount(50).lastObservedValue(12.0).zScore(-7.6).breached(true).build();
            DynamicBaseline smokeBase = DynamicBaseline.builder()
                    .sensorKey("SMOKE_PPM").mean(1.0).stdDev(1.0).lowerBand(0.0).upperBand(4.0)
                    .sampleCount(50).lastObservedValue(95.0).zScore(94.0).breached(true).build();

            when(thresholdService.updateAndEvaluate(any(), any(), eq("TEMPERATURE"), anyDouble())).thenReturn(tempBase);
            when(thresholdService.updateAndEvaluate(any(), any(), eq("HUMIDITY"), anyDouble())).thenReturn(humBase);
            when(thresholdService.updateAndEvaluate(any(), any(), eq("SMOKE_PPM"), anyDouble())).thenReturn(smokeBase);

            // Deduplicator rejects repeated notification
            when(alertDeduplicator.shouldSend(any(), anyString(), anyString())).thenReturn(false);

            inferenceService.processTelemetry(deviceId, orgId, deviceName, 90.0, 12.0, 95.0, false, null);

            // Alert is still persisted in DB
            verify(alertService).createAlert(eq(deviceId), eq("CRITICAL"), anyString(), eq(orgId));
            // But notification is suppressed
            verify(notificationService, never()).send(anyString());
        }
    }

    @Nested
    @DisplayName("Dry-Run Evaluation")
    class DryRunEvaluation {

        @Test
        @DisplayName("Dry-run evaluates telemetry without persisting alerts or mutating state")
        void dryRunEvaluatesWithoutSideEffects() {
            when(thresholdService.getBaselinesForDevice(eq(orgId), eq(deviceId))).thenReturn(Map.of(
                    "TEMPERATURE", DynamicBaseline.builder().sensorKey("TEMPERATURE").mean(25.0).stdDev(2.0).build(),
                    "HUMIDITY", DynamicBaseline.builder().sensorKey("HUMIDITY").mean(50.0).stdDev(5.0).build(),
                    "SMOKE_PPM", DynamicBaseline.builder().sensorKey("SMOKE_PPM").mean(1.0).stdDev(0.5).build()
            ));

            TelemetryAnomalyEvaluationRequest request = TelemetryAnomalyEvaluationRequest.builder()
                    .deviceId(deviceId)
                    .deviceName(deviceName)
                    .temperature(85.0)
                    .humidity(15.0)
                    .smokePpm(60.0)
                    .build();

            AnomalyPredictionResult result = inferenceService.evaluateDryRun(orgId, request);

            assertTrue(result.isAnomaly());
            assertEquals("THERMAL_RUNAWAY", result.anomalyType());
            assertEquals("CRITICAL", result.getSeverity());

            // Zero side-effects
            verify(alertService, never()).createAlert(any(), any(), any(), any());
            verify(notificationService, never()).send(anyString());
        }
    }
}
