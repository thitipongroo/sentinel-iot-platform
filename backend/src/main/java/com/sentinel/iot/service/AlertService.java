package com.sentinel.iot.service;

import com.sentinel.iot.model.Alert;
import com.sentinel.iot.model.SensorCapability;
import com.sentinel.iot.model.SensorReading;
import com.sentinel.iot.repository.AlertRepository;
import com.sentinel.iot.service.notification.AlertDeduplicator;
import io.micrometer.tracing.annotation.NewSpan;
import io.micrometer.tracing.annotation.SpanTag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final AlertRepository alertRepository;
    private final NotificationService notificationService;
    private final BusinessMetricsService businessMetricsService;
    private final AlertDeduplicator alertDeduplicator;

    // ── Global fallback thresholds (used when a device has no capability config) ─
    @Value("${alert.temperature-threshold}")
    private double temperatureThreshold;

    @Value("${alert.humidity-threshold}")
    private double humidityThreshold;

    @Value("${alert.smoke-threshold}")
    private double smokeThreshold;

    // ── Capability-aware evaluation (v2 path) ─────────────────────────────────

    /**
     * Evaluates alerts for all readings in a telemetry payload against the device's
     * declared {@link SensorCapability} map.
     *
     * <p>Each reading is checked independently.  If the device has a capability entry
     * for that sensor, its per-device thresholds apply.  Readings with no matching
     * capability, or with quality != GOOD, are skipped.</p>
     *
     * <p>Falls back to {@link #evaluateLegacy} when {@code capabilities} is null/empty,
     * so v1 and v2 messages are handled uniformly by the Kafka consumer.</p>
     */
    @NewSpan("alert.evaluate")
    public void evaluate(@SpanTag("device.id") UUID deviceId,
                         UUID organizationId,
                         @SpanTag("device.name") String deviceName,
                         Map<String, SensorReading> readings,
                         Map<String, SensorCapability> capabilities) {

        if (readings == null || readings.isEmpty()) return;

        if (capabilities == null || capabilities.isEmpty()) {
            // Device has no capability config — synthesize legacy call from readings
            Double temp  = readingValue(readings, "TEMPERATURE");
            Double hum   = readingValue(readings, "HUMIDITY");
            Double smoke = readingValue(readings, "SMOKE_PPM");
            Boolean mot  = readings.containsKey("MOTION") && readings.get("MOTION").isUsable()
                    ? readings.get("MOTION").value() >= 0.5 : null;
            evaluateLegacy(deviceId, organizationId, deviceName, temp, hum, mot, smoke);
            return;
        }

        for (Map.Entry<String, SensorReading> entry : readings.entrySet()) {
            String key = entry.getKey();
            SensorReading reading = entry.getValue();

            if (!reading.isUsable()) {
                log.debug("Skipping alert for device={} sensor={}: quality={}", deviceName, key, reading.quality());
                continue;
            }

            SensorCapability cap = capabilities.get(key);
            if (cap == null || !cap.enabled()) continue;

            double value = reading.value();

            if (cap.isCritical(value)) {
                String msg = buildMessage(deviceName, key, value, reading.unit(), "CRITICAL", cap.critThreshold(), cap.thresholdDirection());
                createAlert(deviceId, "CRITICAL", msg, organizationId);
                if (alertDeduplicator.shouldSend(deviceId, key, "CRITICAL")) notificationService.send(msg);
            } else if (cap.isWarning(value)) {
                String msg = buildMessage(deviceName, key, value, reading.unit(), "WARNING", cap.warnThreshold(), cap.thresholdDirection());
                createAlert(deviceId, "WARNING", msg, organizationId);
                if (alertDeduplicator.shouldSend(deviceId, key, "WARNING")) notificationService.send(msg);
            }
        }
    }

    // ── Legacy v1 path (global thresholds) ───────────────────────────────────

    /**
     * Original fixed-field alert evaluation.  Kept for backward compatibility and
     * invoked when a device has no capability configuration.
     */
    @NewSpan("alert.evaluate.legacy")
    public void evaluateLegacy(@SpanTag("device.id") UUID deviceId,
                                UUID organizationId,
                                @SpanTag("device.name") String deviceName,
                                Double temperature, Double humidity,
                                Boolean motion, Double smokePpm) {
        if (temperature != null && temperature > temperatureThreshold) {
            String msg = String.format("[%s] CRITICAL: temperature %.1f°C exceeds %.1f°C threshold",
                    deviceName, temperature, temperatureThreshold);
            createAlert(deviceId, "CRITICAL", msg, organizationId);
            if (alertDeduplicator.shouldSend(deviceId, "TEMPERATURE", "CRITICAL")) notificationService.send(msg);
        }

        if (smokePpm != null && smokePpm > smokeThreshold) {
            String msg = String.format("[%s] CRITICAL: smoke detected at %.1f ppm (threshold %.1f ppm)",
                    deviceName, smokePpm, smokeThreshold);
            createAlert(deviceId, "CRITICAL", msg, organizationId);
            if (alertDeduplicator.shouldSend(deviceId, "SMOKE_PPM", "CRITICAL")) notificationService.send(msg);
        }

        if (humidity != null && humidity > humidityThreshold) {
            String msg = String.format("[%s] WARNING: humidity %.1f%% exceeds %.1f%% threshold",
                    deviceName, humidity, humidityThreshold);
            createAlert(deviceId, "WARNING", msg, organizationId);
            if (alertDeduplicator.shouldSend(deviceId, "HUMIDITY", "WARNING")) notificationService.send(msg);
        }

        if (Boolean.TRUE.equals(motion) && temperature != null && temperature > 70) {
            String msg = String.format("[%s] WARNING: motion detected at elevated temperature %.1f°C",
                    deviceName, temperature);
            createAlert(deviceId, "WARNING", msg, organizationId);
        }
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    public Alert createAlert(UUID deviceId, String level, String message, UUID organizationId) {
        Alert alert = new Alert(deviceId, level, message, organizationId);
        Alert saved = alertRepository.save(alert);
        businessMetricsService.recordAlertFired();
        log.warn("Alert created: [{}] {} — {}", level, deviceId, message);
        return saved;
    }

    public List<Alert> getUnacknowledged() {
        return alertRepository.findByAcknowledgedFalseOrderByCreatedAtDesc();
    }

    public List<Alert> getByDevice(UUID deviceId) {
        return alertRepository.findByDeviceIdOrderByCreatedAtDesc(deviceId);
    }

    public Page<Alert> getPage(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return alertRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @org.springframework.transaction.annotation.Transactional
    public int acknowledgeAll() {
        int count = alertRepository.acknowledgeAll();
        log.warn("Bulk acknowledge: {} alerts acknowledged", count);
        return count;
    }

    @SuppressWarnings("null")
    public void acknowledge(UUID alertId) {
        alertRepository.findById(alertId).ifPresent(a -> {
            a.setAcknowledged(true);
            alertRepository.save(a);
        });
    }

    // ── Private utilities ─────────────────────────────────────────────────────

    private static Double readingValue(Map<String, SensorReading> readings, String key) {
        SensorReading r = readings.get(key);
        return (r != null && r.isUsable()) ? r.value() : null;
    }

    private static String buildMessage(String deviceName, String sensorKey, double value,
                                       String unit, String level, Double threshold,
                                       SensorCapability.ThresholdDirection dir) {
        String direction = dir == SensorCapability.ThresholdDirection.ABOVE ? "exceeds" : "dropped below";
        return String.format("[%s] %s: %s = %.2f %s %s threshold %.2f %s",
                deviceName, level, sensorKey, value, unit != null ? unit : "",
                direction, threshold != null ? threshold : 0.0, unit != null ? unit : "");
    }
}
