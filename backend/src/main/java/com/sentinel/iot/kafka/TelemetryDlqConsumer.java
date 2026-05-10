package com.sentinel.iot.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.dto.TelemetryMessage;
import com.sentinel.iot.model.Device;
import com.sentinel.iot.model.DeviceLifecycleStatus;
import com.sentinel.iot.model.Telemetry;
import com.sentinel.iot.repository.DeviceRepository;
import com.sentinel.iot.repository.TelemetryRepository;
import com.sentinel.iot.service.RedisService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Dead-letter consumer for telemetry.dlq — the durable fallback when the main
 * KafkaTelemetryConsumer cannot write a batch to PostgreSQL.
 *
 * Durability guarantee:
 *   telemetry.dlq is configured with 7-day retention. Records survive Redis restarts,
 *   memory pressure, host crashes, and extended DB outages. When the DB recovers,
 *   the DLQ consumer drains automatically; no manual intervention required.
 *
 * Error handling:
 *   AckMode.RECORD with ExponentialBackOff (5 s → max 5 min). If save() fails
 *   (DB still down), the offset is NOT committed → Kafka redelivers with backoff.
 *   There is no second DLQ — records keep retrying until the DB is available.
 *
 * Discard policy:
 *   Unparseable records and records for unknown/decommissioned devices are discarded
 *   (offset committed) to prevent a single bad record from blocking the partition.
 */
@Service
@Slf4j
public class TelemetryDlqConsumer {

    private final TelemetryRepository telemetryRepository;
    private final DeviceRepository deviceRepository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final Counter processedCounter;
    private final Counter discardedCounter;

    public TelemetryDlqConsumer(TelemetryRepository telemetryRepository,
                                 DeviceRepository deviceRepository,
                                 RedisService redisService,
                                 ObjectMapper objectMapper,
                                 MeterRegistry meterRegistry) {
        this.telemetryRepository = telemetryRepository;
        this.deviceRepository    = deviceRepository;
        this.redisService        = redisService;
        this.objectMapper        = objectMapper;
        this.processedCounter    = Counter.builder("sentinel.kafka.dlq.processed")
                .description("DLQ records successfully recovered and written to PostgreSQL")
                .register(meterRegistry);
        this.discardedCounter    = Counter.builder("sentinel.kafka.dlq.discarded")
                .description("DLQ records discarded (unparseable, unknown device, or decommissioned)")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${kafka.topics.telemetry-dlq:telemetry.dlq}",
            containerFactory = "dlqKafkaListenerContainerFactory",
            groupId = "sentinel-telemetry-dlq"
    )
    public void processDlq(String rawPayload) {
        // ── 1. Parse ──────────────────────────────────────────────────────────
        TelemetryMessage msg;
        try {
            msg = objectMapper.readValue(rawPayload, TelemetryMessage.class);
        } catch (Exception e) {
            // Unparseable: discard and commit offset — retrying won't fix a bad payload.
            log.error("DLQ discard — unparseable record (payload cannot be recovered): {}", e.getMessage());
            discardedCounter.increment();
            return;
        }

        // ── 2. Resolve device ──────────────────────────────────────────────────
        Optional<Device> deviceOpt = deviceRepository.findByName(msg.getDeviceId());
        if (deviceOpt.isEmpty()) {
            log.warn("DLQ discard — device not registered: deviceId={}", msg.getDeviceId());
            discardedCounter.increment();
            return;
        }

        Device device = deviceOpt.get();
        DeviceLifecycleStatus status = device.getLifecycleStatus();
        if (status == DeviceLifecycleStatus.INACTIVE || status == DeviceLifecycleStatus.DECOMMISSIONED) {
            log.debug("DLQ discard — device={} is {}", msg.getDeviceId(), status);
            discardedCounter.increment();
            return;
        }

        // ── 3. Persist ────────────────────────────────────────────────────────
        // If save() throws, AckMode.RECORD withholds the offset commit.
        // The dlqKafkaListenerContainerFactory's ExponentialBackOff will redeliver
        // this record with increasing delay until the DB is available.
        Telemetry t = new Telemetry(device.getId(),
                msg.getTemperature(), msg.getHumidity(), msg.getMotion(), msg.getSmokePpm());
        telemetryRepository.save(t);

        // Best-effort Redis cache update — DLQ processing is already delayed,
        // so keeping the cache warm is secondary to DB persistence.
        try {
            redisService.setLatestTelemetry(device.getId().toString(),
                    msg.getTemperature(), msg.getHumidity(), msg.getMotion(), msg.getSmokePpm());
        } catch (Exception e) {
            log.warn("DLQ: Redis cache update failed (non-fatal): {}", e.getMessage());
        }

        processedCounter.increment();
        log.info("DLQ record recovered: device={} temp={}", msg.getDeviceId(), msg.getTemperature());
    }
}
