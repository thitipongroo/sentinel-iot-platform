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

        // ── 2. Bulk device resolution — tenant-aware bulk query ─────────────────
        Set<String> names = new HashSet<>();
        Set<UUID> ids = new HashSet<>();
        for (Parsed p : parsed) {
            String devId = p.msg().getDeviceId();
            if (devId != null) {
                names.add(devId);
                UUID u = tryParseUuid(devId);
                if (u != null) ids.add(u);
            }
        }

        List<Device> resolvedDevices = new ArrayList<>(deviceRepository.findAllByNameIn(names));
        if (!ids.isEmpty()) {
            resolvedDevices.addAll(deviceRepository.findAllById(ids));
        }

        Map<UUID, Device> devicesById = resolvedDevices.stream()
                .collect(Collectors.toMap(Device::getId, Function.identity(), (d1, d2) -> d1));
        Map<String, List<Device>> devicesByName = resolvedDevices.stream()
                .collect(Collectors.groupingBy(Device::getName));

        // ── 3. Build insert list ───────────────────────────────────────────────
        List<Telemetry> toInsert          = new ArrayList<>(parsed.size());
        Map<UUID, Device> devicesToUpdate = new LinkedHashMap<>();
        // Keep accepted pairs so we can do side-effects AFTER the DB commit
        record Accepted(Device device, Telemetry telemetry, String rawPayload) {}
        List<Accepted> accepted = new ArrayList<>(parsed.size());

        for (Parsed p : parsed) {
            TelemetryMessage msg    = p.msg();
            Device           device = null;

            UUID parsedUuid = tryParseUuid(msg.getDeviceId());
            if (parsedUuid != null && devicesById.containsKey(parsedUuid)) {
                Device candidate = devicesById.get(parsedUuid);
                if (msg.getOrganizationId() == null || candidate.getOrganizationId().equals(msg.getOrganizationId())) {
                    device = candidate;
                }
            }

            if (device == null) {
                List<Device> candidates = devicesByName.get(msg.getDeviceId());
                if (candidates != null && !candidates.isEmpty()) {
                    if (msg.getOrganizationId() != null) {
                        // Exact tenant match
                        device = candidates.stream()
                                .filter(d -> d.getOrganizationId().equals(msg.getOrganizationId()))
                                .findFirst()
                                .orElse(null);
                    } else if (candidates.size() == 1) {
                        // Single device matches globally
                        device = candidates.get(0);
                    } else {
                        // Ambiguous: multiple tenants have a device with this name, but payload has no orgId
                        log.warn("Dropping ambiguous telemetry — device name '{}' exists in multiple organizations ({}), payload missing organizationId",
                                msg.getDeviceId(), candidates.stream().map(Device::getOrganizationId).toList());
                        droppedCounter.increment();
                        continue;
                    }
                }
            }

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
            // Redis cache: store tenant-namespaced latest telemetry and status
            redisService.setLatestTelemetry(a.device().getOrganizationId(), a.device().getId().toString(),
                    a.telemetry().getTemperature(), a.telemetry().getHumidity(),
                    a.telemetry().getMotion(), a.telemetry().getSmokePpm());
            redisService.setDeviceStatus(a.device().getOrganizationId(), a.device().getId().toString(), "ONLINE");

            // Capability-aware alert evaluation: uses per-device thresholds when available
            alertService.evaluate(a.device().getId(), a.device().getOrganizationId(),
                    a.device().getName(), a.telemetry().getReadings(), a.device().getCapabilities());

            wsBroadcastPublisher.publish(a.device().getOrganizationId(), a.rawPayload());
        }

        log.debug("Kafka batch: received={} inserted={} dropped={}",
                rawPayloads.size(), toInsert.size(), rawPayloads.size() - toInsert.size());
    }

    private UUID tryParseUuid(String val) {
        if (val == null || val.length() != 36) return null;
        try {
            return UUID.fromString(val);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
