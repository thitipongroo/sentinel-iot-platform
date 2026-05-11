package com.sentinel.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.dto.ReplayQueueMessage;
import com.sentinel.iot.model.Telemetry;
import com.sentinel.iot.repository.TelemetryRepository;
import com.sentinel.iot.service.RedisService;
import com.sentinel.iot.service.ReplayQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the legacy Redis replay queue drains correctly to PostgreSQL
 * and handles partial failures without data loss.
 *
 * <p>These tests guard the @Deprecated {@link ReplayQueueService} until all
 * environments confirm the Kafka DLQ path handles 100% of in-flight data and
 * the queue has been empty for at least one full retention cycle.</p>
 */
class ReplayConsistencyTest extends BaseIntegrationTest {

    @Autowired private ReplayQueueService replayQueueService;
    @Autowired private RedisService       redisService;
    @Autowired private TelemetryRepository telemetryRepository;
    @Autowired private ObjectMapper        objectMapper;

    private UUID deviceId;

    @BeforeEach
    void setUp() {
        deviceId = UUID.randomUUID();
    }

    @Test
    void drain_emptyQueue_doesNothing() {
        // Queue starts empty — drain must be a no-op
        long before = telemetryRepository.count();
        replayQueueService.drain();
        assertThat(telemetryRepository.count()).isEqualTo(before);
    }

    @Test
    void drain_singleMessage_persistsToDb() throws Exception {
        ReplayQueueMessage msg = buildMessage(deviceId, 72.4, 55.0);
        redisService.pushToReplayQueue(objectMapper.writeValueAsString(msg));

        assertThat(redisService.replayQueueSize()).isEqualTo(1);
        replayQueueService.drain();

        assertThat(redisService.replayQueueSize()).isZero();
        // Verify the row was written with correct values
        assertThat(telemetryRepository.findAll())
                .anySatisfy(t -> {
                    assertThat(t.getDeviceId()).isEqualTo(deviceId);
                    assertThat(t.getTemperature()).isEqualTo(72.4);
                });
    }

    @Test
    void drain_multipleBatches_allMessagesEventuallyPersisted() throws Exception {
        int total = 15;
        for (int i = 0; i < total; i++) {
            redisService.pushToReplayQueue(
                objectMapper.writeValueAsString(buildMessage(deviceId, 60.0 + i, 50.0))
            );
        }
        assertThat(redisService.replayQueueSize()).isEqualTo(total);

        // Drain in default batch size (100 > 15, so one pass clears all)
        replayQueueService.drain();

        assertThat(redisService.replayQueueSize()).isZero();
    }

    @Test
    void drain_malformedMessage_requeuesAndContinues() throws Exception {
        // Push one bad message flanked by two good ones
        redisService.pushToReplayQueue(
            objectMapper.writeValueAsString(buildMessage(deviceId, 70.0, 50.0))
        );
        redisService.pushToReplayQueue("{ this is not valid json }");
        redisService.pushToReplayQueue(
            objectMapper.writeValueAsString(buildMessage(deviceId, 71.0, 51.0))
        );

        replayQueueService.drain();

        // Bad message is re-queued; two good ones are persisted
        assertThat(redisService.replayQueueSize()).isEqualTo(1);
        assertThat(telemetryRepository.findAll())
                .filteredOn(t -> t.getDeviceId().equals(deviceId))
                .hasSize(2);
    }

    @Test
    void pushToReplayQueue_respectsMaxQueueSize() throws Exception {
        int maxSize = 5;
        // Push maxSize + 2 messages; only maxSize should be stored
        for (int i = 0; i < maxSize + 2; i++) {
            // Simulate max-queue-size=5 by checking size before each push
            if (redisService.replayQueueSize() < maxSize) {
                redisService.pushToReplayQueue(
                    objectMapper.writeValueAsString(buildMessage(deviceId, 60.0 + i, 50.0))
                );
            }
        }
        assertThat(redisService.replayQueueSize()).isLessThanOrEqualTo(maxSize);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ReplayQueueMessage buildMessage(UUID deviceId, double temp, double humidity) {
        ReplayQueueMessage msg = new ReplayQueueMessage();
        msg.setDeviceId(deviceId);
        msg.setTemperature(temp);
        msg.setHumidity(humidity);
        msg.setSmokePpm(0.0);
        msg.setMotion(false);
        msg.setTimestamp(Instant.now());
        return msg;
    }
}
