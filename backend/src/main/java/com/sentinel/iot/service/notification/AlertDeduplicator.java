package com.sentinel.iot.service.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Distributed alert deduplication via Redis SET NX PX.
 *
 * Key schema (DB 0, shared with telemetry cache):
 *   alert:dedup:{deviceId}:{sensorKey}:{severity}   String  TTL = cooldown window
 *
 * setIfAbsent() returns true  → key did not exist → send notification, key is now set
 * setIfAbsent() returns false → key exists        → suppress (still within cooldown)
 *
 * Because the key lives in Redis, all replicas share the same dedup state —
 * a single burst produces exactly one notification regardless of replica count.
 */
@Component
@Slf4j
public class AlertDeduplicator {

    private static final String KEY_PREFIX = "alert:dedup:";

    private final StringRedisTemplate redis;

    @Value("${notification.dedup.cooldown-minutes:5}")
    private long cooldownMinutes;

    @Value("${notification.dedup.enabled:true}")
    private boolean enabled;

    public AlertDeduplicator(
            @Qualifier("redisTemplate") StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Returns true if a notification should be sent for this device + sensor + severity.
     * The Redis key is created atomically with a TTL equal to the cooldown window.
     *
     * Fail-open: if Redis is unavailable the notification is sent without dedup
     * rather than crashing the Kafka consumer batch (which has no try/catch around
     * alertService.evaluate() and treats Redis as best-effort).
     */
    @SuppressWarnings("null")
    public boolean shouldSend(UUID deviceId, String sensorKey, String severity) {
        if (!enabled) return true;

        String key = KEY_PREFIX + deviceId + ":" + sensorKey + ":" + severity;
        try {
            Boolean set = redis.opsForValue().setIfAbsent(key, "1", Duration.ofMinutes(cooldownMinutes));
            if (Boolean.TRUE.equals(set)) {
                return true;
            }
            log.debug("Alert suppressed (cooldown {}m): device={} sensor={} severity={}",
                    cooldownMinutes, deviceId, sensorKey, severity);
            return false;
        } catch (Exception e) {
            log.warn("AlertDeduplicator: Redis unavailable — sending without dedup: {}", e.getMessage());
            return true;
        }
    }
}
