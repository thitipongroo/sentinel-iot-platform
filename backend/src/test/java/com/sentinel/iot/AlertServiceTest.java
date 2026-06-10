package com.sentinel.iot;

import com.sentinel.iot.model.Alert;
import com.sentinel.iot.model.SensorCapability;
import com.sentinel.iot.model.SensorReading;
import com.sentinel.iot.repository.AlertRepository;
import com.sentinel.iot.service.AlertService;
import com.sentinel.iot.service.BusinessMetricsService;
import com.sentinel.iot.service.NotificationService;
import com.sentinel.iot.service.notification.AlertDeduplicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("AlertService")
@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock AlertRepository alertRepository;
    @Mock NotificationService notificationService;
    @Mock BusinessMetricsService businessMetricsService;
    @Mock AlertDeduplicator alertDeduplicator;
    @InjectMocks AlertService alertService;

    private final UUID deviceId = UUID.fromString("dddddddd-0000-0000-0000-000000000001");
    private final UUID orgId    = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @SuppressWarnings("null")
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(alertService, "temperatureThreshold", 80.0);
        ReflectionTestUtils.setField(alertService, "humidityThreshold", 90.0);
        ReflectionTestUtils.setField(alertService, "smokeThreshold", 200.0);
        lenient().when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(alertDeduplicator.shouldSend(any(), anyString(), anyString())).thenReturn(true);
    }

    // ── Legacy threshold evaluation (empty capabilities → global thresholds) ──

    @Nested
    @DisplayName("Legacy global-threshold evaluation")
    class LegacyThresholdEvaluation {

        @SuppressWarnings("null")
        @ParameterizedTest(name = "{0} = {1} {2} → {3} alert")
        @CsvSource({
            "TEMPERATURE, 85.0, °C,   CRITICAL, temperature",
            "SMOKE_PPM,  250.0, ppm,  CRITICAL, smoke",
            "HUMIDITY,   95.0,  %,    WARNING,  humidity"
        })
        @DisplayName("creates alert when sensor exceeds its threshold")
        void evaluate_createsAlertWhenSensorExceedsThreshold(
                String sensor, double value, String unit,
                String expectedLevel, String messageKeyword) {

            Map<String, SensorReading> readings = Map.of(sensor, SensorReading.good(value, unit));
            alertService.evaluate(deviceId, orgId, "sensor-1", readings, Map.of());

            ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
            verify(alertRepository, atLeastOnce()).save(captor.capture());
            assertThat(captor.getAllValues())
                    .as("expected %s alert containing '%s'", expectedLevel, messageKeyword)
                    .anyMatch(a -> expectedLevel.equals(a.getLevel())
                            && a.getMessage().contains(messageKeyword));
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("creates no alert when all readings are below thresholds")
        void evaluate_createsNoAlert_whenBelowAllThresholds() {
            Map<String, SensorReading> readings = Map.of(
                    "TEMPERATURE", SensorReading.good(70.0, "°C"),
                    "HUMIDITY",    SensorReading.good(60.0, "%"),
                    "SMOKE_PPM",   SensorReading.good(10.0, "ppm")
            );
            alertService.evaluate(deviceId, orgId, "sensor-1", readings, Map.of());

            verify(alertRepository, never()).save(any());
            verify(notificationService, never()).send(any());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("creates WARNING when motion detected at elevated temperature")
        void evaluate_createsWarning_whenMotionDetectedAtElevatedTemperature() {
            Map<String, SensorReading> readings = Map.of(
                    "TEMPERATURE", SensorReading.good(75.0, "°C"),
                    "MOTION",      SensorReading.good(1.0, "")
            );
            alertService.evaluate(deviceId, orgId, "sensor-1", readings, Map.of());

            ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
            verify(alertRepository, atLeastOnce()).save(captor.capture());
            assertThat(captor.getAllValues())
                    .anyMatch(a -> "WARNING".equals(a.getLevel())
                            && a.getMessage().contains("motion"));
        }

        @Test
        @DisplayName("does not alert at exactly threshold value (strictly-greater check)")
        void evaluateLegacy_atExactThreshold_doesNotCreateAlert() {
            alertService.evaluateLegacy(deviceId, orgId, "sensor-1", 80.0, 90.0, null, 200.0);

            verify(alertRepository, never()).save(any());
        }

        @Test
        @DisplayName("does nothing when readings map is null")
        void evaluate_doesNothing_whenReadingsIsNull() {
            assertThatNoException().isThrownBy(
                    () -> alertService.evaluate(deviceId, orgId, "sensor-1", null, Map.of()));
            verify(alertRepository, never()).save(any());
        }

        @Test
        @DisplayName("does nothing when readings map is empty")
        void evaluate_doesNothing_whenReadingsIsEmpty() {
            assertThatNoException().isThrownBy(
                    () -> alertService.evaluate(deviceId, orgId, "sensor-1", Map.of(), Map.of()));
            verify(alertRepository, never()).save(any());
        }
    }

    // ── Capability-aware evaluation (v2 path) ─────────────────────────────────

    @Nested
    @DisplayName("Capability-aware evaluation (v2)")
    class CapabilityAwareEvaluation {

        @SuppressWarnings("null")
        @Test
        @DisplayName("creates CRITICAL alert when reading exceeds critical threshold")
        void evaluate_createsCriticalAlert_whenAboveCriticalThreshold() {
            Map<String, SensorCapability> caps = Map.of(
                    "TEMPERATURE", SensorCapability.above("°C", 75.0, 90.0, 1));
            Map<String, SensorReading> readings = Map.of(
                    "TEMPERATURE", SensorReading.good(95.0, "°C"));

            alertService.evaluate(deviceId, orgId, "sensor-1", readings, caps);

            ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
            verify(alertRepository, atLeastOnce()).save(captor.capture());
            assertThat(captor.getAllValues())
                    .as("should produce a CRITICAL alert")
                    .anyMatch(a -> "CRITICAL".equals(a.getLevel()));
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("creates WARNING alert when reading is between warn and critical thresholds")
        void evaluate_createsWarningAlert_whenBetweenWarnAndCritThreshold() {
            Map<String, SensorCapability> caps = Map.of(
                    "TEMPERATURE", SensorCapability.above("°C", 75.0, 90.0, 1));
            Map<String, SensorReading> readings = Map.of(
                    "TEMPERATURE", SensorReading.good(80.0, "°C"));

            alertService.evaluate(deviceId, orgId, "sensor-1", readings, caps);

            ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
            verify(alertRepository, atLeastOnce()).save(captor.capture());
            assertThat(captor.getAllValues())
                    .anyMatch(a -> "WARNING".equals(a.getLevel()));
        }

        @Test
        @DisplayName("creates no alert when reading is below warn threshold")
        void evaluate_noAlert_whenBelowWarnThreshold() {
            Map<String, SensorCapability> caps = Map.of(
                    "TEMPERATURE", SensorCapability.above("°C", 75.0, 90.0, 1));
            Map<String, SensorReading> readings = Map.of(
                    "TEMPERATURE", SensorReading.good(70.0, "°C"));

            alertService.evaluate(deviceId, orgId, "sensor-1", readings, caps);

            verify(alertRepository, never()).save(any());
        }

        @Test
        @DisplayName("skips reading when no capability entry exists for that sensor key")
        void evaluate_skipsReading_whenNoCapabilityEntryForSensor() {
            Map<String, SensorCapability> caps = Map.of(
                    "HUMIDITY", SensorCapability.above("%", 80.0, 95.0, 0));
            Map<String, SensorReading> readings = Map.of(
                    "TEMPERATURE", SensorReading.good(100.0, "°C"));

            alertService.evaluate(deviceId, orgId, "sensor-1", readings, caps);

            verify(alertRepository, never()).save(any());
        }

        @Test
        @DisplayName("skips reading when capability is disabled")
        void evaluate_skipsReading_whenCapabilityIsDisabled() {
            SensorCapability disabled = new SensorCapability(
                    "°C", null, null, 75.0, 90.0,
                    SensorCapability.ThresholdDirection.ABOVE, false, 1);
            Map<String, SensorCapability> caps = Map.of("TEMPERATURE", disabled);
            Map<String, SensorReading> readings = Map.of(
                    "TEMPERATURE", SensorReading.good(100.0, "°C"));

            alertService.evaluate(deviceId, orgId, "sensor-1", readings, caps);

            verify(alertRepository, never()).save(any());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("creates WARNING when BELOW-direction value drops below warn threshold")
        void evaluate_below_createsWarning_whenValueDropsBelowWarnThreshold() {
            Map<String, SensorCapability> caps = Map.of(
                    "BATTERY_PCT", SensorCapability.below("%", 20.0, 10.0, 0));
            Map<String, SensorReading> readings = Map.of(
                    "BATTERY_PCT", SensorReading.good(15.0, "%"));

            alertService.evaluate(deviceId, orgId, "sensor-1", readings, caps);

            ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
            verify(alertRepository, atLeastOnce()).save(captor.capture());
            assertThat(captor.getAllValues())
                    .anyMatch(a -> "WARNING".equals(a.getLevel()));
        }
    }

    // ── Deduplicator gating ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Deduplicator gating")
    class DeduplicatorGating {

        @SuppressWarnings("null")
        @Test
        @DisplayName("persists alert but suppresses notification when deduplicator returns false")
        void evaluate_suppressesNotification_whenDeduplicatorReturnsFalse() {
            when(alertDeduplicator.shouldSend(deviceId, "TEMPERATURE", "CRITICAL"))
                    .thenReturn(false);
            Map<String, SensorReading> readings = Map.of(
                    "TEMPERATURE", SensorReading.good(85.0, "°C"));

            alertService.evaluate(deviceId, orgId, "sensor-1", readings, Map.of());

            verify(alertRepository, atLeastOnce()).save(any());
            verify(notificationService, never()).send(any());
        }
    }

    // ── State management ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("State management")
    class StateManagement {

        @SuppressWarnings("null")
        @Test
        @DisplayName("acknowledgeAll delegates to repository and returns affected count")
        void acknowledgeAll_delegatesToRepository() {
            when(alertRepository.acknowledgeAll()).thenReturn(5);

            assertThat(alertService.acknowledgeAll())
                    .as("should return count from repository")
                    .isEqualTo(5);
            verify(alertRepository).acknowledgeAll();
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("getUnacknowledged returns list from repository")
        void getUnacknowledged_delegatesToRepository() {
            Alert a = new Alert(deviceId, "CRITICAL", "test", orgId);
            when(alertRepository.findByAcknowledgedFalseOrderByCreatedAtDesc())
                    .thenReturn(List.of(a));

            assertThat(alertService.getUnacknowledged()).hasSize(1);
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("getByDevice delegates to repository with correct device ID")
        void getByDevice_delegatesToRepository() {
            alertService.getByDevice(deviceId);

            verify(alertRepository).findByDeviceIdOrderByCreatedAtDesc(deviceId);
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("createAlert records business metric and persists alert")
        void createAlert_recordsMetricAndPersistsAlert() {
            alertService.createAlert(deviceId, "CRITICAL", "test alert", orgId);

            verify(businessMetricsService).recordAlertFired();
            verify(alertRepository).save(any(Alert.class));
        }
    }
}
