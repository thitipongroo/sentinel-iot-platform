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
 * Drains the Redis replay queue and re-persists buffered telemetry to PostgreSQL
 * once the circuit breaker reports the database is healthy again.
 *
 * The job runs every {@code telemetry.replay.interval-ms} milliseconds (default 30 s).
 * It is skipped entirely when the telemetryDB circuit breaker is OPEN.
 * Failed messages are pushed back to the tail of the queue for the next cycle.
 */
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
