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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    // ---- v1 legacy path (empty capabilities → global thresholds) -----------

    @SuppressWarnings("null")
    @Test
    void evaluate_shouldCreateCriticalAlertWhenTemperatureExceedsThreshold() {
        Map<String, SensorReading> readings = Map.of(
                "TEMPERATURE", SensorReading.good(85.0, "°C")
        );
        Map<String, SensorCapability> capabilities = Map.of();
        alertService.evaluate(deviceId, orgId, "sensor-1", readings, capabilities);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(a ->
                "CRITICAL".equals(a.getLevel()) && a.getMessage().contains("temperature"));
        verify(notificationService, atLeastOnce()).send(anyString());
    }

    @SuppressWarnings("null")
    @Test
    void evaluate_shouldCreateCriticalAlertWhenSmokeExceedsThreshold() {
        Map<String, SensorReading> readings = Map.of(
                "SMOKE_PPM", SensorReading.good(250.0, "ppm")
        );
        Map<String, SensorCapability> capabilities = Map.of();
        alertService.evaluate(deviceId, orgId, "sensor-1", readings, capabilities);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(a ->
                "CRITICAL".equals(a.getLevel()) && a.getMessage().contains("smoke"));
    }

    @SuppressWarnings("null")
    @Test
    void evaluate_shouldCreateWarningWhenHumidityExceedsThreshold() {
        Map<String, SensorReading> readings = Map.of(
                "HUMIDITY", SensorReading.good(95.0, "%")
        );
        Map<String, SensorCapability> capabilities = Map.of();
        alertService.evaluate(deviceId, orgId, "sensor-1", readings, capabilities);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(a ->
                "WARNING".equals(a.getLevel()) && a.getMessage().contains("humidity"));
    }

    @SuppressWarnings("null")
    @Test
    void evaluate_shouldCreateNoAlertWhenBelowAllThresholds() {
        Map<String, SensorReading> readings = Map.of(
                "TEMPERATURE", SensorReading.good(70.0, "°C"),
                "HUMIDITY",    SensorReading.good(60.0, "%"),
                "SMOKE_PPM",   SensorReading.good(10.0, "ppm")
        );
        Map<String, SensorCapability> capabilities = Map.of();
        alertService.evaluate(deviceId, orgId, "sensor-1", readings, capabilities);
        verify(alertRepository, never()).save(any());
        verify(notificationService, never()).send(any());
    }

    @SuppressWarnings("null")
    @Test
    void evaluate_shouldCreateWarningWhenMotionDetectedAtElevatedTemperature() {
        Map<String, SensorReading> readings = Map.of(
                "TEMPERATURE", SensorReading.good(75.0, "°C"),
                "MOTION",      SensorReading.good(1.0, "")
        );
        Map<String, SensorCapability> capabilities = Map.of();
        alertService.evaluate(deviceId, orgId, "sensor-1", readings, capabilities);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(a ->
                "WARNING".equals(a.getLevel()) && a.getMessage().contains("motion"));
    }

    @SuppressWarnings("null")
    @Test
    void evaluateLegacy_atExactThreshold_doesNotCreateAlert() {
        // threshold is 80.0 — value must be strictly > threshold
        alertService.evaluateLegacy(deviceId, orgId, "sensor-1", 80.0, 90.0, null, 200.0);

        verify(alertRepository, never()).save(any());
    }

    // ---- v2 capability-aware path ------------------------------------------

    @SuppressWarnings("null")
    @Test
    void evaluate_v2_createsCriticalAlert_whenCapabilityThresholdExceeded() {
        Map<String, SensorCapability> caps = Map.of(
                "TEMPERATURE", SensorCapability.above("°C", 75.0, 90.0, 1));
        Map<String, SensorReading> readings = Map.of(
                "TEMPERATURE", SensorReading.good(95.0, "°C"));

        alertService.evaluate(deviceId, orgId, "sensor-1", readings, caps);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(a -> "CRITICAL".equals(a.getLevel()));
    }

    @SuppressWarnings("null")
    @Test
    void evaluate_v2_createsWarningAlert_whenBetweenWarnAndCritThreshold() {
        Map<String, SensorCapability> caps = Map.of(
                "TEMPERATURE", SensorCapability.above("°C", 75.0, 90.0, 1));
        Map<String, SensorReading> readings = Map.of(
                "TEMPERATURE", SensorReading.good(80.0, "°C"));

        alertService.evaluate(deviceId, orgId, "sensor-1", readings, caps);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(a -> "WARNING".equals(a.getLevel()));
    }

    @SuppressWarnings("null")
    @Test
    void evaluate_v2_noAlert_whenBelowWarnThreshold() {
        Map<String, SensorCapability> caps = Map.of(
                "TEMPERATURE", SensorCapability.above("°C", 75.0, 90.0, 1));
        Map<String, SensorReading> readings = Map.of(
                "TEMPERATURE", SensorReading.good(70.0, "°C"));

        alertService.evaluate(deviceId, orgId, "sensor-1", readings, caps);

        verify(alertRepository, never()).save(any());
    }

    @SuppressWarnings("null")
    @Test
    void evaluate_v2_skipsReading_whenNoCapabilityEntryForSensor() {
        Map<String, SensorCapability> caps = Map.of(
                "HUMIDITY", SensorCapability.above("%", 80.0, 95.0, 0));
        // Only TEMPERATURE reading, no TEMPERATURE capability
        Map<String, SensorReading> readings = Map.of(
                "TEMPERATURE", SensorReading.good(100.0, "°C"));

        alertService.evaluate(deviceId, orgId, "sensor-1", readings, caps);

        verify(alertRepository, never()).save(any());
    }

    @SuppressWarnings("null")
    @Test
    void evaluate_v2_skipsReading_whenCapabilityIsDisabled() {
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
    void evaluate_v2_below_capability_alertsWhenValueDropsBelowThreshold() {
        Map<String, SensorCapability> caps = Map.of(
                "BATTERY_PCT", SensorCapability.below("%", 20.0, 10.0, 0));
        Map<String, SensorReading> readings = Map.of(
                "BATTERY_PCT", SensorReading.good(15.0, "%")); // below warn=20, above crit=10

        alertService.evaluate(deviceId, orgId, "sensor-1", readings, caps);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(a -> "WARNING".equals(a.getLevel()));
    }

    // ---- deduplicator gating -----------------------------------------------

    @SuppressWarnings("null")
    @Test
    void evaluate_deduplicator_suppressesNotification_whenShouldSendReturnsFalse() {
        when(alertDeduplicator.shouldSend(deviceId, "TEMPERATURE", "CRITICAL")).thenReturn(false);
        Map<String, SensorReading> readings = Map.of(
                "TEMPERATURE", SensorReading.good(85.0, "°C"));

        alertService.evaluate(deviceId, orgId, "sensor-1", readings, Map.of());

        verify(alertRepository, atLeastOnce()).save(any()); // alert still persisted
        verify(notificationService, never()).send(any());   // but no notification
    }

    // ---- acknowledgeAll / getters ------------------------------------------

    @SuppressWarnings("null")
    @Test
    void acknowledgeAll_delegatesToRepository() {
        when(alertRepository.acknowledgeAll()).thenReturn(5);

        int count = alertService.acknowledgeAll();

        assertThat(count).isEqualTo(5);
        verify(alertRepository).acknowledgeAll();
    }

    @SuppressWarnings("null")
    @Test
    void getUnacknowledged_delegatesToRepository() {
        Alert a = new Alert(deviceId, "CRITICAL", "test", orgId);
        when(alertRepository.findByAcknowledgedFalseOrderByCreatedAtDesc()).thenReturn(List.of(a));

        List<Alert> result = alertService.getUnacknowledged();

        assertThat(result).hasSize(1);
    }

    @SuppressWarnings("null")
    @Test
    void getByDevice_delegatesToRepository() {
        when(alertRepository.findByDeviceIdOrderByCreatedAtDesc(deviceId)).thenReturn(List.of());

        alertService.getByDevice(deviceId);

        verify(alertRepository).findByDeviceIdOrderByCreatedAtDesc(deviceId);
    }

    @SuppressWarnings("null")
    @Test
    void createAlert_recordsMetric() {
        alertService.createAlert(deviceId, "CRITICAL", "test alert", orgId);

        verify(businessMetricsService).recordAlertFired();
        verify(alertRepository).save(any(Alert.class));
    }

    // ---- null/empty guard --------------------------------------------------

    @Test
    void evaluate_doesNothing_whenReadingsIsNull() {
        assertThatNoException().isThrownBy(
                () -> alertService.evaluate(deviceId, orgId, "sensor-1", null, Map.of()));
        verify(alertRepository, never()).save(any());
    }

    @Test
    void evaluate_doesNothing_whenReadingsIsEmpty() {
        assertThatNoException().isThrownBy(
                () -> alertService.evaluate(deviceId, orgId, "sensor-1", Map.of(), Map.of()));
        verify(alertRepository, never()).save(any());
    }
}
