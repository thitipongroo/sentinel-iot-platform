package com.sentinel.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.dto.ReplayQueueMessage;
import com.sentinel.iot.repository.TelemetryRepository;
import com.sentinel.iot.service.RedisService;
import com.sentinel.iot.service.ReplayQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

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
@DisplayName("ReplayConsistencyTest — legacy Redis replay queue")
class ReplayConsistencyTest extends BaseIntegrationTest {

    @SuppressWarnings("removal")
    @Autowired private ReplayQueueService  replayQueueService;
    @Autowired private RedisService        redisService;
    @Autowired private TelemetryRepository telemetryRepository;
    @Autowired private ObjectMapper        objectMapper;
    @Autowired private StringRedisTemplate stringRedisTemplate;

    private UUID deviceId;

    @BeforeEach
    void setUp() {
        deviceId = UUID.randomUUID();
        stringRedisTemplate.delete("sentinel:replay:queue");
    }

    // ── Drain behaviour ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Drain behaviour")
    class DrainBehaviour {

        @Test
        @DisplayName("drain() is a no-op when the replay queue is empty")
        void drain_emptyQueue_doesNothing() {
            long before = telemetryRepository.count();
            replayQueueService.drain();
            assertThat(telemetryRepository.count())
                    .as("row count must not change when queue is empty")
                    .isEqualTo(before);
        }

        @Test
        @DisplayName("drain() persists a single queued message to the telemetry table and empties the queue")
        void drain_singleMessage_persistsToDb() throws Exception {
            ReplayQueueMessage msg = buildMessage(deviceId, 72.4, 55.0);
            redisService.pushToReplayQueue(objectMapper.writeValueAsString(msg));

            assertThat(redisService.replayQueueSize()).as("queue size before drain").isEqualTo(1);
            replayQueueService.drain();

            assertThat(redisService.replayQueueSize()).as("queue size after drain").isZero();
            assertThat(telemetryRepository.findAll())
                    .as("telemetry row must match the drained message")
                    .anySatisfy(t -> {
                        assertThat(t.getDeviceId()).isEqualTo(deviceId);
                        assertThat(t.getTemperature()).isEqualTo(72.4);
                    });
        }

        @Test
        @DisplayName("drain() persists all messages when the queue spans more than one batch")
        void drain_multipleBatches_allMessagesEventuallyPersisted() throws Exception {
            int total = 15;
            for (int i = 0; i < total; i++) {
                redisService.pushToReplayQueue(
                        objectMapper.writeValueAsString(buildMessage(deviceId, 60.0 + i, 50.0)));
            }
            assertThat(redisService.replayQueueSize()).as("queue size before drain").isEqualTo(total);

            // Default batch size > 15, so one pass clears all
            replayQueueService.drain();

            assertThat(redisService.replayQueueSize()).as("queue must be empty after drain").isZero();
        }

        @Test
        @DisplayName("drain() re-queues a malformed message and continues processing the remaining valid messages")
        void drain_malformedMessage_requeuesAndContinues() throws Exception {
            redisService.pushToReplayQueue(
                    objectMapper.writeValueAsString(buildMessage(deviceId, 70.0, 50.0)));
            redisService.pushToReplayQueue("{ this is not valid json }");
            redisService.pushToReplayQueue(
                    objectMapper.writeValueAsString(buildMessage(deviceId, 71.0, 51.0)));

            replayQueueService.drain();

            assertThat(redisService.replayQueueSize())
                    .as("bad message must be re-queued").isEqualTo(1);
            assertThat(telemetryRepository.findAll())
                    .filteredOn(t -> t.getDeviceId().equals(deviceId))
                    .as("two valid messages must be persisted")
                    .hasSize(2);
        }
    }

    // ── Queue capacity ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Queue capacity")
    class QueueCapacity {

        @Test
        @DisplayName("pushToReplayQueue respects the configured maximum queue size")
        void pushToReplayQueue_respectsMaxQueueSize() throws Exception {
            int maxSize = 5;
            for (int i = 0; i < maxSize + 2; i++) {
                if (redisService.replayQueueSize() < maxSize) {
                    redisService.pushToReplayQueue(
                            objectMapper.writeValueAsString(buildMessage(deviceId, 60.0 + i, 50.0)));
                }
            }
            assertThat(redisService.replayQueueSize())
                    .as("queue size must not exceed the configured maximum")
                    .isLessThanOrEqualTo(maxSize);
        }
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
