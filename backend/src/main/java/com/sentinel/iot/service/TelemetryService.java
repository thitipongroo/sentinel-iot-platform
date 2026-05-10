package com.sentinel.iot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.dto.ReplayQueueMessage;
import com.sentinel.iot.model.Telemetry;
import com.sentinel.iot.repository.TelemetryRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.annotation.NewSpan;
import io.micrometer.tracing.annotation.SpanTag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class TelemetryService {

    private final TelemetryRepository telemetryRepository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final Counter telemetryCounter;
    private final Counter telemetryDroppedCounter;

    public TelemetryService(TelemetryRepository telemetryRepository,
                            RedisService redisService,
                            ObjectMapper objectMapper,
                            MeterRegistry meterRegistry) {
        this.telemetryRepository    = telemetryRepository;
        this.redisService           = redisService;
        this.objectMapper           = objectMapper;
        this.telemetryCounter       = Counter.builder("sentinel.telemetry.received")
                .description("Total telemetry messages received via MQTT")
                .register(meterRegistry);
        this.telemetryDroppedCounter = Counter.builder("sentinel.telemetry.dropped")
                .description("Telemetry messages buffered to replay queue due to DB unavailability")
                .register(meterRegistry);
        Gauge.builder("sentinel.replay.queue.size", redisService, RedisService::replayQueueSize)
                .description("Number of buffered telemetry messages waiting for DB recovery")
                .register(meterRegistry);
    }

    @NewSpan("telemetry.save")
    @Retry(name = "telemetryDB", fallbackMethod = "saveFallback")
    @CircuitBreaker(name = "telemetryDB", fallbackMethod = "saveFallback")
    public Telemetry save(@SpanTag("device.id") UUID deviceId,
                          Double temperature, Double humidity, Boolean motion, Double smokePpm) {
        Telemetry t = new Telemetry(deviceId, temperature, humidity, motion, smokePpm);
        Telemetry saved = telemetryRepository.save(t);
        redisService.setLatestTelemetry(deviceId.toString(), temperature, humidity, motion, smokePpm);
        telemetryCounter.increment();
        return saved;
    }

    /**
     * DB is unavailable — update the Redis cache (so the dashboard stays live) and
     * push to the replay queue so the reading is persisted once the DB recovers.
     */
    public Telemetry saveFallback(UUID deviceId, Double temperature, Double humidity,
                                  Boolean motion, Double smokePpm, Throwable cause) {
        log.error("DB unavailable for device={}, buffering to replay queue: {}", deviceId, cause.getMessage());

        redisService.setLatestTelemetry(deviceId.toString(), temperature, humidity, motion, smokePpm);

        try {
            ReplayQueueMessage msg = new ReplayQueueMessage(
                    deviceId, temperature, humidity, motion, smokePpm, Instant.now());
            redisService.pushToReplayQueue(objectMapper.writeValueAsString(msg));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize replay message for device={}: {}", deviceId, e.getMessage());
        }

        telemetryDroppedCounter.increment();
        return null;
    }

    public List<Telemetry> getLatest(UUID deviceId, int limit) {
        return telemetryRepository.findByDeviceIdOrderByTimestampDesc(
                deviceId, PageRequest.of(0, limit));
    }

    public List<Telemetry> getRange(UUID deviceId, Instant from, Instant to) {
        return telemetryRepository.findByDeviceIdAndTimestampBetween(deviceId, from, to);
    }

    public long countLastMinute() {
        return telemetryRepository.countByTimestampAfter(Instant.now().minusSeconds(60));
    }
}
