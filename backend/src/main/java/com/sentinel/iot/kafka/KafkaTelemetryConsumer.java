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
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import com.sentinel.iot.anomaly.service.StreamingAnomalyInferenceService;

import java.nio.charset.StandardCharsets;
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
 * <p>Multi-tenant Collision Protection: extracts tenant context from message payload,
 * Kafka partition key (<orgId>:<deviceId>), or Kafka record headers (tenant-id).
 * Resolves devices within their tenant boundary to prevent data collision between
 * organizations that use identical sensor names.</p>
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
    private final StreamingAnomalyInferenceService anomalyInferenceService;

    public KafkaTelemetryConsumer(TelemetryRepository telemetryRepository,
                                  DeviceRepository deviceRepository,
                                  AlertService alertService,
                                  WebSocketBroadcastPublisher wsBroadcastPublisher,
                                  RedisService redisService,
                                  ObjectMapper objectMapper,
                                  MeterRegistry meterRegistry) {
        this(telemetryRepository, deviceRepository, alertService, wsBroadcastPublisher,
                redisService, objectMapper, meterRegistry, null);
    }

    @Autowired
    public KafkaTelemetryConsumer(TelemetryRepository telemetryRepository,
                                  DeviceRepository deviceRepository,
                                  AlertService alertService,
                                  WebSocketBroadcastPublisher wsBroadcastPublisher,
                                  RedisService redisService,
                                  ObjectMapper objectMapper,
                                  MeterRegistry meterRegistry,
                                  @Autowired(required = false) StreamingAnomalyInferenceService anomalyInferenceService) {
        this.telemetryRepository  = telemetryRepository;
        this.deviceRepository     = deviceRepository;
        this.alertService         = alertService;
        this.wsBroadcastPublisher = wsBroadcastPublisher;
        this.redisService         = redisService;
        this.objectMapper         = objectMapper;
        this.anomalyInferenceService = anomalyInferenceService;
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
    public void consumeBatch(List<?> batch) {
        if (batch == null || batch.isEmpty()) return;

        List<ConsumerRecord<String, String>> records = new ArrayList<>(batch.size());
        for (Object item : batch) {
            if (item instanceof ConsumerRecord<?, ?> cr) {
                String key = cr.key() != null ? cr.key().toString() : null;
                String value = cr.value() != null ? cr.value().toString() : null;
                ConsumerRecord<String, String> typed = new ConsumerRecord<>(
                        cr.topic(), cr.partition(), cr.offset(), key, value);
                if (cr.headers() != null) {
                    cr.headers().forEach(h -> typed.headers().add(h.key(), h.value()));
                }
                records.add(typed);
            } else if (item instanceof String raw) {
                records.add(new ConsumerRecord<>("telemetry.raw", 0, 0L, null, raw));
            } else {
                log.warn("Unexpected item type in Kafka batch: {}", item.getClass());
            }
        }
        processBatch(records);
    }

    private void processBatch(List<ConsumerRecord<String, String>> records) {
        // ── 1. Parse all records (both v1 and v2) ─────────────────────────────
        record Parsed(TelemetryMessage msg, String rawPayload, String recordKey, Map<String, String> headers) {}
        List<Parsed> parsed = new ArrayList<>(records.size());
        for (ConsumerRecord<String, String> rec : records) {
            String raw = rec.value();
            if (raw == null) continue;
            try {
                TelemetryMessage msg = objectMapper.readValue(raw, TelemetryMessage.class);
                Map<String, String> headers = new HashMap<>();
                if (rec.headers() != null) {
                    for (var h : rec.headers()) {
                        headers.put(h.key(), new String(h.value(), StandardCharsets.UTF_8));
                    }
                }
                parsed.add(new Parsed(msg, raw, rec.key(), headers));
            } catch (Exception e) {
                log.warn("Dropping unparseable Kafka record: {}", e.getMessage());
                droppedCounter.increment();
            }
        }
        if (parsed.isEmpty()) return;

        // ── 2. Multi-tenant key extraction & fallback ─────────────────────────
        for (Parsed p : parsed) {
            TelemetryMessage msg = p.msg();
            if (msg.getOrganizationId() == null) {
                // Check Kafka headers
                String headerOrg = p.headers().get("tenant-id");
                if (headerOrg == null) headerOrg = p.headers().get("X-Organization-Id");
                if (headerOrg == null) headerOrg = p.headers().get("orgId");
                UUID orgFromHeader = tryParseUuid(headerOrg);
                if (orgFromHeader != null) {
                    msg.setOrganizationId(orgFromHeader);
                } else if (p.recordKey() != null && p.recordKey().contains(":")) {
                    // Check Kafka key: <orgId>:<deviceId>
                    String prefix = p.recordKey().substring(0, p.recordKey().indexOf(':'));
                    UUID orgFromKey = tryParseUuid(prefix);
                    if (orgFromKey != null) {
                        msg.setOrganizationId(orgFromKey);
                    }
                }
            }
        }

        // ── 3. Bulk device resolution — tenant-aware bulk query ─────────────────
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
        // Composite tenant-isolated map: orgId:name -> Device
        Map<String, Device> devicesByTenantAndName = new HashMap<>();
        for (Device d : resolvedDevices) {
            if (d.getOrganizationId() != null && d.getName() != null) {
                devicesByTenantAndName.put(d.getOrganizationId() + ":" + d.getName(), d);
            }
        }

        // ── 4. Build insert list with strict multi-tenant validation ───────────
        List<Telemetry> toInsert          = new ArrayList<>(parsed.size());
        Map<UUID, Device> devicesToUpdate = new LinkedHashMap<>();
        // Keep accepted pairs so we can do side-effects AFTER the DB commit
        record Accepted(Device device, Telemetry telemetry, String rawPayload) {}
        List<Accepted> accepted = new ArrayList<>(parsed.size());

        for (Parsed p : parsed) {
            TelemetryMessage msg    = p.msg();
            Device           device = null;
            UUID             orgId  = msg.getOrganizationId();
            String           devId  = msg.getDeviceId();

            UUID parsedUuid = tryParseUuid(devId);
            if (parsedUuid != null && devicesById.containsKey(parsedUuid)) {
                Device candidate = devicesById.get(parsedUuid);
                // Strict multi-tenant verification:
                if (orgId != null) {
                    if (candidate.getOrganizationId().equals(orgId)) {
                        device = candidate;
                    } else {
                        log.warn("Dropping cross-tenant collision: deviceId={} belongs to org {} but message claimed org {}",
                                parsedUuid, candidate.getOrganizationId(), orgId);
                        droppedCounter.increment();
                        continue;
                    }
                } else {
                    // UUID uniquely identifies device primary key
                    device = candidate;
                }
            }

            if (device == null) {
                if (orgId != null) {
                    // Exact match within tenant boundary
                    device = devicesByTenantAndName.get(orgId + ":" + devId);
                } else {
                    // Un-namespaced payload: search by device name
                    List<Device> candidates = devicesByName.get(devId);
                    if (candidates != null && !candidates.isEmpty()) {
                        if (candidates.size() == 1) {
                            // Single device matches globally
                            device = candidates.get(0);
                        } else {
                            // Ambiguous: multiple tenants have a device with this name, but payload has no orgId
                            log.warn("Dropping ambiguous telemetry — device name '{}' exists in multiple organizations ({}), payload missing organizationId",
                                    devId, candidates.stream().map(Device::getOrganizationId).toList());
                            droppedCounter.increment();
                            continue;
                        }
                    }
                }
            }

            if (device == null) {
                log.debug("Dropping telemetry — unregistered device={}", devId);
                droppedCounter.increment();
                continue;
            }
            if (device.getLifecycleStatus() == DeviceLifecycleStatus.INACTIVE
                    || device.getLifecycleStatus() == DeviceLifecycleStatus.DECOMMISSIONED) {
                log.debug("Dropping telemetry — device={} is {}", devId, device.getLifecycleStatus());
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

        // ── 5. Batch DB writes — persist first, side-effects after ─────────────
        if (!toInsert.isEmpty()) {
            telemetryRepository.saveAll(toInsert);
            processedCounter.increment(toInsert.size());
        }
        if (!devicesToUpdate.isEmpty()) {
            deviceRepository.saveAll(devicesToUpdate.values());
        }

        // ── 6. Post-commit side-effects: Redis cache, alerts, WebSocket ────────
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

            // Phase 4: Streaming ML anomaly inference & adaptive dynamic threshold evaluation
            if (anomalyInferenceService != null) {
                anomalyInferenceService.processTelemetry(
                        a.device().getId(),
                        a.device().getOrganizationId(),
                        a.device().getName(),
                        a.telemetry().getTemperature(),
                        a.telemetry().getHumidity(),
                        a.telemetry().getSmokePpm(),
                        a.telemetry().getMotion(),
                        a.telemetry().getReadings());
            }

            wsBroadcastPublisher.publish(a.device().getOrganizationId(), a.rawPayload());
        }

        log.debug("Kafka batch: received={} inserted={} dropped={}",
                records.size(), toInsert.size(), records.size() - toInsert.size());
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
