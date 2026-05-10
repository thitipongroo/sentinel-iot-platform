package com.sentinel.iot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.dto.ReplayQueueMessage;
import com.sentinel.iot.model.Telemetry;
import com.sentinel.iot.repository.TelemetryRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @deprecated Superseded by the Kafka Dead Letter Topic (telemetry.dlq) pattern.
 *
 * <p><b>Why this existed:</b> Before Kafka was introduced, the MQTT consumer wrote
 * directly to PostgreSQL. On DB failure, the Resilience4j circuit breaker opened and
 * {@code TelemetryService.saveFallback()} buffered records to a Redis List. This service
 * drained that list once the circuit re-closed.
 *
 * <p><b>Why Redis Lists are not sufficient for durability:</b>
 * <ul>
 *   <li>Redis data lives in memory — a crash, eviction under memory pressure, or
 *       accidental {@code FLUSHALL} silently loses every buffered record.</li>
 *   <li>AOF persistence mitigates crash loss but does not protect against eviction
 *       or administrative errors.</li>
 *   <li>Redis has no built-in replay semantics — the drain loop is custom code
 *       with its own failure modes (push-back creates unbounded queue growth).</li>
 * </ul>
 *
 * <p><b>Current design:</b> The Kafka consumer ({@code KafkaTelemetryConsumer}) uses
 * {@code AckMode.BATCH}: on {@code saveAll()} failure the offset is not committed and
 * Kafka redelivers. After 3 retries the {@code DefaultErrorHandler} routes each record
 * individually to {@code telemetry.dlq} (7-day retention, replicated Kafka topic).
 * {@code TelemetryDlqConsumer} then reprocesses with exponential backoff until the DB
 * recovers — no Redis involved, no data loss risk beyond Kafka's own retention window.
 *
 * <p>This class is kept to drain any records that were buffered before the migration.
 * It will be removed once the Redis replay queue is confirmed empty in all environments.
 */
@Deprecated(since = "Kafka DLQ introduced", forRemoval = true)
@Service
@Slf4j
public class ReplayQueueService {

    private static final String CB_NAME = "telemetryDB";

    private final RedisService redisService;
    private final TelemetryRepository telemetryRepository;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final ObjectMapper objectMapper;
    private final Counter replaySuccessCounter;
    private final Counter replayFailureCounter;

    @Value("${telemetry.replay.batch-size:100}")
    private int batchSize;

    public ReplayQueueService(RedisService redisService,
                              TelemetryRepository telemetryRepository,
                              CircuitBreakerRegistry circuitBreakerRegistry,
                              ObjectMapper objectMapper,
                              MeterRegistry meterRegistry) {
        this.redisService            = redisService;
        this.telemetryRepository     = telemetryRepository;
        this.circuitBreakerRegistry  = circuitBreakerRegistry;
        this.objectMapper            = objectMapper;
        this.replaySuccessCounter    = Counter.builder("sentinel.replay.success")
                .description("Telemetry messages successfully replayed from queue")
                .register(meterRegistry);
        this.replayFailureCounter    = Counter.builder("sentinel.replay.failure")
                .description("Telemetry messages that failed replay and were re-queued")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${telemetry.replay.interval-ms:30000}")
    public void drain() {
        long queueSize = redisService.replayQueueSize();
        if (queueSize == 0) {
            return;
        }

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(CB_NAME);
        if (cb.getState() == CircuitBreaker.State.OPEN) {
            log.debug("Replay skipped — {} circuit breaker is OPEN (buffered={})", CB_NAME, queueSize);
            return;
        }

        log.info("Starting replay: buffered={} batchSize={} cbState={}", queueSize, batchSize, cb.getState());

        List<String> batch = redisService.drainReplayQueue(batchSize);
        int replayed = 0;
        int failed   = 0;

        for (String raw : batch) {
            try {
                ReplayQueueMessage msg = objectMapper.readValue(raw, ReplayQueueMessage.class);

                Telemetry t = new Telemetry();
                t.setDeviceId(msg.getDeviceId());
                t.setTemperature(msg.getTemperature());
                t.setHumidity(msg.getHumidity());
                t.setMotion(msg.getMotion());
                t.setSmokePpm(msg.getSmokePpm());
                t.setTimestamp(msg.getTimestamp());

                telemetryRepository.save(t);
                replaySuccessCounter.increment();
                replayed++;

            } catch (Exception e) {
                log.warn("Replay failed for message, re-queuing: {}", e.getMessage());
                redisService.pushToReplayQueue(raw);
                replayFailureCounter.increment();
                failed++;
            }
        }

        log.info("Replay complete: replayed={} re-queued={} remaining={}",
                replayed, failed, redisService.replayQueueSize());
    }
}
