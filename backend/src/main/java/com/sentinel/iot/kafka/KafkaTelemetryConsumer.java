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
import com.sentinel.iot.websocket.TelemetryWebSocketHandler;
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
 * from Kafka per poll cycle and writes them to PostgreSQL in a single saveAll() call.
 *
 * Scaling: add partitions to telemetry.raw and increase concurrency on this listener
 * (spring.kafka.listener.concurrency) to saturate multiple consumer threads.
 *
 * Resilience: AckMode.BATCH means the offset is committed only after saveAll() returns
 * successfully. On DB failure, the offset is NOT committed and Kafka redelivers the batch.
 */
@Service
@Slf4j
public class KafkaTelemetryConsumer {

    private final TelemetryRepository telemetryRepository;
    private final DeviceRepository deviceRepository;
    private final AlertService alertService;
    private final TelemetryWebSocketHandler webSocketHandler;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final Counter processedCounter;
    private final Counter droppedCounter;

    public KafkaTelemetryConsumer(TelemetryRepository telemetryRepository,
                                   DeviceRepository deviceRepository,
                                   AlertService alertService,
                                   TelemetryWebSocketHandler webSocketHandler,
                                   RedisService redisService,
                                   ObjectMapper objectMapper,
                                   MeterRegistry meterRegistry) {
        this.telemetryRepository = telemetryRepository;
        this.deviceRepository    = deviceRepository;
        this.alertService        = alertService;
        this.webSocketHandler    = webSocketHandler;
        this.redisService        = redisService;
        this.objectMapper        = objectMapper;
        this.processedCounter    = Counter.builder("sentinel.kafka.telemetry.processed")
                .description("Telemetry records written to PostgreSQL via Kafka batch ingest")
                .register(meterRegistry);
        this.droppedCounter      = Counter.builder("sentinel.kafka.telemetry.dropped")
                .description("Telemetry Kafka records skipped (parse error, unknown device, lifecycle gate)")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${kafka.topics.telemetry-raw:telemetry.raw}",
            containerFactory = "batchKafkaListenerContainerFactory",
            groupId = "sentinel-telemetry-ingest"
    )
    public void consumeBatch(List<String> rawPayloads) {
        // ── 1. Parse all records ─────────────────────────────────────────────────
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

        if (parsed.isEmpty()) {
            return;
        }

        // ── 2. Bulk device resolution — one DB query for the entire batch ────────
        Set<String> names = parsed.stream()
                .map(p -> p.msg().getDeviceId())
                .collect(Collectors.toSet());
        Map<String, Device> deviceMap = deviceRepository.findAllByNameIn(names).stream()
                .collect(Collectors.toMap(Device::getName, Function.identity()));

        // ── 3. Build insert list + per-record side-effects ───────────────────────
        List<Telemetry> toInsert = new ArrayList<>(parsed.size());
        // LinkedHashMap preserves insertion order; last update for a device ID wins
        Map<UUID, Device> devicesToUpdate = new LinkedHashMap<>();

        for (Parsed p : parsed) {
            TelemetryMessage msg = p.msg();
            Device device = deviceMap.get(msg.getDeviceId());

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

            toInsert.add(new Telemetry(device.getId(),
                    msg.getTemperature(), msg.getHumidity(), msg.getMotion(), msg.getSmokePpm()));

            device.setStatus("ONLINE");
            device.setLastSeen(Instant.now());
            devicesToUpdate.put(device.getId(), device);

            // Redis cache + alert + WebSocket are per-record — order matters for live dashboard
            redisService.setLatestTelemetry(device.getId().toString(),
                    msg.getTemperature(), msg.getHumidity(), msg.getMotion(), msg.getSmokePpm());
            alertService.evaluate(device.getId(), device.getName(),
                    msg.getTemperature(), msg.getHumidity(), msg.getMotion(), msg.getSmokePpm());
            webSocketHandler.broadcast(p.rawPayload());
        }

        // ── 4. Batch DB writes ───────────────────────────────────────────────────
        // Hibernate jdbc.batch_size=50 groups these into ceil(N/50) batch statements.
        // If either saveAll() throws, AckMode.BATCH withholds the offset commit and
        // Kafka redelivers this batch — no message loss without a separate DLQ.
        if (!toInsert.isEmpty()) {
            telemetryRepository.saveAll(toInsert);
            processedCounter.increment(toInsert.size());
        }
        if (!devicesToUpdate.isEmpty()) {
            deviceRepository.saveAll(devicesToUpdate.values());
        }

        log.debug("Kafka batch complete: received={} inserted={} dropped={}",
                rawPayloads.size(), toInsert.size(), rawPayloads.size() - toInsert.size());
    }
}
