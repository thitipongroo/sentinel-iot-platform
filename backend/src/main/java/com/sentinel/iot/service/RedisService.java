package com.sentinel.iot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final StringRedisTemplate redis;

    private static final String STATUS_PREFIX    = "device:status:";
    private static final String TELEMETRY_PREFIX = "device:telemetry:";
    private static final String REPLAY_QUEUE_KEY = "sentinel:replay:queue";
    private static final Duration TTL = Duration.ofMinutes(10);

    @Value("${telemetry.replay.max-queue-size:10000}")
    private int maxQueueSize;

    public void setDeviceStatus(String deviceId, String status) {
        redis.opsForValue().set(STATUS_PREFIX + deviceId, status, TTL);
    }

    public String getDeviceStatus(String deviceId) {
        return redis.opsForValue().get(STATUS_PREFIX + deviceId);
    }

    public void setLatestTelemetry(String deviceId, double temperature, double humidity,
                                   Boolean motion, Double smokePpm) {
        Map<String, String> fields = new HashMap<>();
        fields.put("temperature", String.valueOf(temperature));
        fields.put("humidity", String.valueOf(humidity));
        fields.put("motion", motion != null ? String.valueOf(motion) : "false");
        fields.put("smokePpm", smokePpm != null ? String.valueOf(smokePpm) : "0.0");
        fields.put("ts", String.valueOf(System.currentTimeMillis()));
        redis.opsForHash().putAll(TELEMETRY_PREFIX + deviceId, fields);
        redis.expire(TELEMETRY_PREFIX + deviceId, TTL);
    }

    public Map<Object, Object> getLatestTelemetry(String deviceId) {
        return redis.opsForHash().entries(TELEMETRY_PREFIX + deviceId);
    }

    // ── Replay queue (offline buffering) ─────────────────────────────────────

    public void pushToReplayQueue(String serializedMessage) {
        Long size = redis.opsForList().size(REPLAY_QUEUE_KEY);
        if (size == null || size < maxQueueSize) {
            redis.opsForList().rightPush(REPLAY_QUEUE_KEY, serializedMessage);
        }
    }

    public List<String> drainReplayQueue(int batchSize) {
        List<String> messages = redis.opsForList().leftPop(REPLAY_QUEUE_KEY, batchSize);
        return messages != null ? messages : Collections.emptyList();
    }

    public long replayQueueSize() {
        Long size = redis.opsForList().size(REPLAY_QUEUE_KEY);
        return size != null ? size : 0;
    }
}
