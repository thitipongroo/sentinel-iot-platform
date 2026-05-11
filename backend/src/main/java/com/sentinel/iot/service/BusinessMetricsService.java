package com.sentinel.iot.service;

import com.sentinel.iot.repository.AlertRepository;
import com.sentinel.iot.repository.DeviceRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Registers business-level Prometheus metrics that reflect platform health from
 * the customer's perspective, not just infrastructure health.
 *
 * <p>Metrics exposed:
 * <ul>
 *   <li>{@code sentinel.business.active_devices}     — devices with ONLINE status (Gauge, refreshed every 30s)</li>
 *   <li>{@code sentinel.business.total_devices}      — all registered devices (Gauge, refreshed every 30s)</li>
 *   <li>{@code sentinel.business.unack_alerts_total} — unacknowledged alerts (Gauge, refreshed every 30s)</li>
 *   <li>{@code sentinel.business.alert_fired_total}  — cumulative alerts created (Counter, incremented on create)</li>
 *   <li>{@code sentinel.business.dlq_total}          — cumulative DLQ routes = ingestion failure signal (linked from MqttConsumerService)</li>
 * </ul>
 * </p>
 */
@Service
@Slf4j
public class BusinessMetricsService {

    private final DeviceRepository  deviceRepository;
    private final AlertRepository   alertRepository;

    private final AtomicLong activeDevices  = new AtomicLong(0);
    private final AtomicLong totalDevices   = new AtomicLong(0);
    private final AtomicLong unackAlerts    = new AtomicLong(0);
    private final Counter    alertFiredCounter;

    public BusinessMetricsService(DeviceRepository deviceRepository,
                                  AlertRepository alertRepository,
                                  MeterRegistry meterRegistry) {
        this.deviceRepository = deviceRepository;
        this.alertRepository  = alertRepository;

        Gauge.builder("sentinel.business.active_devices", activeDevices, AtomicLong::get)
                .description("Devices currently reporting as ONLINE")
                .register(meterRegistry);

        Gauge.builder("sentinel.business.total_devices", totalDevices, AtomicLong::get)
                .description("Total registered devices")
                .register(meterRegistry);

        Gauge.builder("sentinel.business.unack_alerts", unackAlerts, AtomicLong::get)
                .description("Unacknowledged alerts — rising value indicates alert fatigue")
                .register(meterRegistry);

        this.alertFiredCounter = Counter.builder("sentinel.business.alert_fired")
                .description("Cumulative alerts created (all levels)")
                .register(meterRegistry);
    }

    /** Called by AlertService whenever an alert is persisted. */
    public void recordAlertFired() {
        alertFiredCounter.increment();
    }

    /** Refreshes device + alert gauges every 30 seconds. */
    @Scheduled(fixedDelayString = "${metrics.business.refresh-interval-ms:30000}")
    public void refresh() {
        try {
            activeDevices.set(deviceRepository.countByStatus("ONLINE"));
            totalDevices.set(deviceRepository.count());
            unackAlerts.set(alertRepository.countByAcknowledgedFalse());
        } catch (Exception e) {
            log.warn("Business metrics refresh failed: {}", e.getMessage());
        }
    }
}
