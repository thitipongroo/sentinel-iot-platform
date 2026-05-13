package com.sentinel.iot.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.dto.TelemetryMessage;
import com.sentinel.iot.model.Device;
import com.sentinel.iot.model.DeviceLifecycleStatus;
import com.sentinel.iot.model.Telemetry;
import com.sentinel.iot.repository.DeviceRepository;
import com.sentinel.iot.repository.TelemetryRepository;
import com.sentinel.iot.service.AlertService;
import com.sentinel.iot.service.RedisService;
import com.sentinel.iot.websocket.WebSocketBroadcastPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Batch ingest worker — reads up to max-poll-records (500) raw telemetry payloads
 * per Kafka poll cycle and writes them to PostgreSQL in a single saveAll() call.
 *
 * <p>Supports both v1 (fixed fields) and v2 (readings map + edge metadata) payloads.
 * The {@link Telemetry#from} factory handles the schema branching; this class is
 * schema-version agnostic.</p>
 *
 * <p>Capability-aware alert evaluation: if the device has declared sensor capabilities,
 * per-device thresholds are used.  Falls back to global application.yml thresholds
 * for devices without a capability config.</p>
 */
@Service
@Slf4j
public class KafkaTelemetryConsumer {

    private final TelemetryRepository     telemetryRepository;
    private final DeviceRepository        deviceRepository;
    private final AlertService            alertService;
    private final WebSocketBroadcastPublisher wsBroadcastPublisher;
    private final RedisService            redisService;
    private final ObjectMapper            objectMapper;
    private final Counter                 processedCounter;
    private final Counter                 droppedCounter;

    public KafkaTelemetryConsumer(TelemetryRepository telemetryRepository,
                                  DeviceRepository deviceRepository,
                                  AlertService alertService,
                                  WebSocketBroadcastPublisher wsBroadcastPublisher,
                                  RedisService redisService,
                                  ObjectMapper objectMapper,
                                  MeterRegistry meterRegistry) {
        this.telemetryRepository  = telemetryRepository;
        this.deviceRepository     = deviceRepository;
        this.alertService         = alertService;
        this.wsBroadcastPublisher = wsBroadcastPublisher;
        this.redisService         = redisService;
        this.objectMapper         = objectMapper;
        this.processedCounter     = Counter.builder("sentinel.kafka.telemetry.processed")
                .description("Telemetry records written to PostgreSQL via Kafka batch ingest")
                .register(meterRegistry);
        this.droppedCounter       = Counter.builder("sentinel.kafka.telemetry.dropped")
                .description("Kafka records skipped (parse error, unknown device, lifecycle gate, bad quality)")
                .register(meterRegistry);
    }

    @SuppressWarnings("null")
    @KafkaListener(
            topics            = "${kafka.topics.telemetry-raw:telemetry.raw}",
            containerFactory  = "batchKafkaListenerContainerFactory",
            groupId           = "sentinel-telemetry-ingest"
    )
    public void consumeBatch(List<String> rawPayloads) {

        // ── 1. Parse all records (both v1 and v2) ─────────────────────────────
        record Parsed(TelemetryMessage msg, String rawPayload) {}
        List<Parsed> parsed = new ArrayList<>(rawPayloads.size());
        for (String raw : rawPayloads) {
            try {
                parsed.add(new Parsed(objectMapper.readValue(raw, TelemetryMessage.class), raw));
            } catch (Exception e) {
                log.warn("Dropping unparseable Kafka record: {}", e.getMessage());
                droppedCounter.increment();
            }
        }
        if (parsed.isEmpty()) return;

        // ── 2. Bulk device resolution — one DB query for the whole batch ───────
        Set<String> names = parsed.stream()
                .map(p -> p.msg().getDeviceId())
                .collect(Collectors.toSet());
        Map<String, Device> deviceMap = deviceRepository.findAllByNameIn(names).stream()
                .collect(Collectors.toMap(Device::getName, Function.identity()));

        // ── 3. Build insert list ───────────────────────────────────────────────
        List<Telemetry> toInsert          = new ArrayList<>(parsed.size());
        Map<UUID, Device> devicesToUpdate = new LinkedHashMap<>();
        // Keep accepted pairs so we can do side-effects AFTER the DB commit
        record Accepted(Device device, Telemetry telemetry, String rawPayload) {}
        List<Accepted> accepted = new ArrayList<>(parsed.size());

        for (Parsed p : parsed) {
            TelemetryMessage msg    = p.msg();
            Device           device = deviceMap.get(msg.getDeviceId());

            if (device == null) {
                log.debug("Dropping telemetry — unregistered device={}", msg.getDeviceId());
                droppedCounter.increment();
                continue;
            }
            if (device.getLifecycleStatus() == DeviceLifecycleStatus.INACTIVE
                    || device.getLifecycleStatus() == DeviceLifecycleStatus.DECOMMISSIONED) {
                log.debug("Dropping telemetry — device={} is {}", msg.getDeviceId(), device.getLifecycleStatus());
                droppedCounter.increment();
                continue;
            }

            // Build Telemetry using the schema-version-aware factory
            Telemetry t = Telemetry.from(msg, device.getId());
            toInsert.add(t);
            accepted.add(new Accepted(device, t, p.rawPayload()));

            device.setStatus("ONLINE");
            device.setLastSeen(Instant.now());
            devicesToUpdate.put(device.getId(), device);
        }

        // ── 4. Batch DB writes — persist first, side-effects after ─────────────
        if (!toInsert.isEmpty()) {
            telemetryRepository.saveAll(toInsert);
            processedCounter.increment(toInsert.size());
        }
        if (!devicesToUpdate.isEmpty()) {
            deviceRepository.saveAll(devicesToUpdate.values());
        }

        // ── 5. Post-commit side-effects: Redis cache, alerts, WebSocket ────────
        // Running these AFTER saveAll ensures we never broadcast or fire an alert
        // for a record that failed to persist. Kafka retries are idempotent because
        // alerts are evaluated per-batch and Redis is best-effort.
        for (Accepted a : accepted) {
            // Redis cache: store the unified readings map for fast dashboard reads
            redisService.setLatestTelemetry(a.device().getId().toString(),
                    a.telemetry().getTemperature(), a.telemetry().getHumidity(),
                    a.telemetry().getMotion(), a.telemetry().getSmokePpm());

            // Capability-aware alert evaluation: uses per-device thresholds when available
            alertService.evaluate(a.device().getId(), a.device().getOrganizationId(),
                    a.device().getName(), a.telemetry().getReadings(), a.device().getCapabilities());

            wsBroadcastPublisher.publish(a.device().getOrganizationId(), a.rawPayload());
        }

        log.debug("Kafka batch: received={} inserted={} dropped={}",
                rawPayloads.size(), toInsert.size(), rawPayloads.size() - toInsert.size());
    }
}
