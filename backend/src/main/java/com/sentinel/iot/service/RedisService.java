package com.sentinel.iot.service;

import com.sentinel.iot.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis key schema (tenant-namespaced):
 *
 * <pre>
 *   device:status:{orgId}:{deviceId}              String  TTL=10m        Online/Offline
 *   device:telemetry:{orgId}:{deviceId}           Hash    TTL=10m        Latest readings
 *   sentinel:replay:queue                         List    No TTL         Offline buffer (org-agnostic)
 *   alert:dedup:{deviceId}:{sensorKey}:{severity} String  TTL=cooldown   Distributed notification dedup (see AlertDeduplicator)
 * </pre>
 *
 * The orgId prefix ensures that cache keys from different tenants never collide,
 * even if device UUIDs were somehow shared (defence-in-depth on top of DB RLS).
 *
 * When the calling thread has no tenant context (e.g. ReplayQueueService draining)
 * the raw deviceId is used as-is — replay messages are always resolved to a specific
 * device UUID so there is no cross-tenant risk.
 */
@Service
@RequiredArgsConstructor
@SuppressWarnings("null") // Spring RedisTemplate ops() methods are declared @Nullable but never return null in practice
public class RedisService {

    private final StringRedisTemplate redis;

    private static final String STATUS_PREFIX    = "device:status:";
    private static final String TELEMETRY_PREFIX = "device:telemetry:";
    private static final String REPLAY_QUEUE_KEY = "sentinel:replay:queue";
    private static final Duration TTL = Duration.ofMinutes(10);

    @Value("${telemetry.replay.max-queue-size:10000}")
    private int maxQueueSize;

    // ── Tenant-namespaced key helpers ─────────────────────────────────────────

    private String statusKey(String deviceId) {
        java.util.UUID orgId = TenantContext.get();
        return orgId != null
                ? STATUS_PREFIX + orgId + ":" + deviceId
                : STATUS_PREFIX + deviceId;
    }

    private String telemetryKey(String deviceId) {
        java.util.UUID orgId = TenantContext.get();
        return orgId != null
                ? TELEMETRY_PREFIX + orgId + ":" + deviceId
                : TELEMETRY_PREFIX + deviceId;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void setDeviceStatus(String deviceId, String status) {
        setDeviceStatus(TenantContext.get(), deviceId, status);
    }

    public void setDeviceStatus(java.util.UUID orgId, String deviceId, String status) {
        String key = orgId != null
                ? STATUS_PREFIX + orgId + ":" + deviceId
                : STATUS_PREFIX + deviceId;
        redis.opsForValue().set(key, status, TTL);
    }

    public String getDeviceStatus(String deviceId) {
        return redis.opsForValue().get(statusKey(deviceId));
    }

    public void setLatestTelemetry(String deviceId, Double temperature, Double humidity,
                                   Boolean motion, Double smokePpm) {
        setLatestTelemetry(TenantContext.get(), deviceId, temperature, humidity, motion, smokePpm);
    }

    public void setLatestTelemetry(java.util.UUID orgId, String deviceId, Double temperature, Double humidity,
                                   Boolean motion, Double smokePpm) {
        Map<String, String> fields = new HashMap<>();
        fields.put("temperature", temperature != null ? String.valueOf(temperature) : "0.0");
        fields.put("humidity", humidity != null ? String.valueOf(humidity) : "0.0");
        fields.put("motion", motion != null ? String.valueOf(motion) : "false");
        fields.put("smokePpm", smokePpm != null ? String.valueOf(smokePpm) : "0.0");
        fields.put("ts", String.valueOf(System.currentTimeMillis()));
        String key = orgId != null
                ? TELEMETRY_PREFIX + orgId + ":" + deviceId
                : TELEMETRY_PREFIX + deviceId;
        redis.opsForHash().putAll(key, fields);
        redis.expire(key, TTL);
    }

    public Map<Object, Object> getLatestTelemetry(String deviceId) {
        return redis.opsForHash().entries(telemetryKey(deviceId));
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
