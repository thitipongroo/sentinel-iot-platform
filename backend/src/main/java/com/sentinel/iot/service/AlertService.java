package com.sentinel.iot.service;

import com.sentinel.iot.model.Alert;
import com.sentinel.iot.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final AlertRepository alertRepository;
    private final NotificationService notificationService;

    @Value("${alert.temperature-threshold}")
    private double temperatureThreshold;

    @Value("${alert.humidity-threshold}")
    private double humidityThreshold;

    @Value("${alert.smoke-threshold}")
    private double smokeThreshold;

    public void evaluate(UUID deviceId, String deviceName,
                         double temperature, double humidity,
                         Boolean motion, Double smokePpm) {
        if (temperature > temperatureThreshold) {
            String msg = String.format("[%s] CRITICAL: temperature %.1f°C exceeds %.1f°C threshold",
                    deviceName, temperature, temperatureThreshold);
            createAlert(deviceId, "CRITICAL", msg);
            notificationService.send(msg);
        }

        if (smokePpm != null && smokePpm > smokeThreshold) {
            String msg = String.format("[%s] CRITICAL: smoke detected at %.1f ppm (threshold %.1f ppm)",
                    deviceName, smokePpm, smokeThreshold);
            createAlert(deviceId, "CRITICAL", msg);
            notificationService.send(msg);
        }

        if (humidity > humidityThreshold) {
            String msg = String.format("[%s] WARNING: humidity %.1f%% exceeds %.1f%% threshold",
                    deviceName, humidity, humidityThreshold);
            createAlert(deviceId, "WARNING", msg);
            notificationService.send(msg);
        }

        if (Boolean.TRUE.equals(motion) && temperature > 70) {
            String msg = String.format("[%s] WARNING: motion detected at elevated temperature %.1f°C",
                    deviceName, temperature);
            createAlert(deviceId, "WARNING", msg);
        }
    }

    public Alert createAlert(UUID deviceId, String level, String message) {
        Alert alert = new Alert(deviceId, level, message);
        Alert saved = alertRepository.save(alert);
        log.warn("Alert created: [{}] {} - {}", level, deviceId, message);
        return saved;
    }

    public List<Alert> getUnacknowledged() {
        return alertRepository.findByAcknowledgedFalseOrderByCreatedAtDesc();
    }

    public List<Alert> getRecent() {
        return alertRepository.findTop50ByOrderByCreatedAtDesc();
    }

    public void acknowledge(UUID alertId) {
        alertRepository.findById(alertId).ifPresent(a -> {
            a.setAcknowledged(true);
            alertRepository.save(a);
        });
    }
}
