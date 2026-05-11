package com.sentinel.iot;

import com.sentinel.iot.model.Alert;
import com.sentinel.iot.model.SensorCapability;
import com.sentinel.iot.model.SensorReading;
import com.sentinel.iot.repository.AlertRepository;
import com.sentinel.iot.service.AlertService;
import com.sentinel.iot.service.BusinessMetricsService;
import com.sentinel.iot.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock AlertRepository alertRepository;
    @Mock NotificationService notificationService;
    @Mock BusinessMetricsService businessMetricsService;
    @InjectMocks AlertService alertService;

    private final UUID deviceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(alertService, "temperatureThreshold", 80.0);
        ReflectionTestUtils.setField(alertService, "humidityThreshold", 90.0);
        ReflectionTestUtils.setField(alertService, "smokeThreshold", 200.0);
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void evaluate_shouldCreateCriticalAlertWhenTemperatureExceedsThreshold() {
        Map<String, SensorReading> readings = Map.of(
                "TEMPERATURE", SensorReading.good(85.0, "°C")
        );
        Map<String, SensorCapability> capabilities = Map.of();
        alertService.evaluate(deviceId, UUID.randomUUID(), "sensor-1", readings, capabilities);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(a ->
                "CRITICAL".equals(a.getLevel()) && a.getMessage().contains("temperature"));
        verify(notificationService, atLeastOnce()).send(anyString());
    }

    @Test
    void evaluate_shouldCreateCriticalAlertWhenSmokeExceedsThreshold() {
        Map<String, SensorReading> readings = Map.of(
                "SMOKE_PPM", SensorReading.good(250.0, "ppm")
        );
        Map<String, SensorCapability> capabilities = Map.of();
        alertService.evaluate(deviceId, UUID.randomUUID(), "sensor-1", readings, capabilities);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(a ->
                "CRITICAL".equals(a.getLevel()) && a.getMessage().contains("smoke"));
    }

    @Test
    void evaluate_shouldCreateWarningWhenHumidityExceedsThreshold() {
        Map<String, SensorReading> readings = Map.of(
                "HUMIDITY", SensorReading.good(95.0, "%")
        );
        Map<String, SensorCapability> capabilities = Map.of();
        alertService.evaluate(deviceId, UUID.randomUUID(), "sensor-1", readings, capabilities);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(a ->
                "WARNING".equals(a.getLevel()) && a.getMessage().contains("humidity"));
    }

    @Test
    void evaluate_shouldCreateNoAlertWhenBelowAllThresholds() {
        Map<String, SensorReading> readings = Map.of(
                "TEMPERATURE", SensorReading.good(70.0, "°C"),
                "HUMIDITY",    SensorReading.good(60.0, "%"),
                "SMOKE_PPM",   SensorReading.good(10.0, "ppm")
        );
        Map<String, SensorCapability> capabilities = Map.of();
        alertService.evaluate(deviceId, UUID.randomUUID(), "sensor-1", readings, capabilities);
        verify(alertRepository, never()).save(any());
        verify(notificationService, never()).send(any());
    }

    @Test
    void evaluate_shouldCreateWarningWhenMotionDetectedAtElevatedTemperature() {
        Map<String, SensorReading> readings = Map.of(
                "TEMPERATURE", SensorReading.good(75.0, "°C"),
                "MOTION",      SensorReading.good(1.0, "")
        );
        Map<String, SensorCapability> capabilities = Map.of();
        alertService.evaluate(deviceId, UUID.randomUUID(), "sensor-1", readings, capabilities);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(a ->
                "WARNING".equals(a.getLevel()) && a.getMessage().contains("motion"));
    }
}
