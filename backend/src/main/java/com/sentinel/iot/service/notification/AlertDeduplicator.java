package com.sentinel.iot.service.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Distributed alert deduplication via Redis SET NX PX with local in-memory fallback.
 *
 * Primary Tier (Distributed):
 *   Key schema (DB 0, shared with telemetry cache):
 *     alert:dedup:{deviceId}:{sensorKey}:{severity}   String  TTL = cooldown window
 *
 *   setIfAbsent() returns true  → key did not exist → send notification, key is now set
 *   setIfAbsent() returns false → key exists        → suppress (still within cooldown)
 *
 * Fallback Tier (In-Memory Resilience):
 *   If Redis is unreachable, an in-memory concurrent cache prevents alert storms while
 *   ensuring the initial alert is still delivered.
 */
@Component
@Slf4j
public class AlertDeduplicator {

    private static final String KEY_PREFIX = "alert:dedup:";

    private final StringRedisTemplate redis;
    private final Map<String, Instant> localFallbackCache = new ConcurrentHashMap<>();

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
     * If Redis is unavailable, falls back to the in-memory cache to suppress duplicate
     * notifications within the cooldown window while guaranteeing the first alert is delivered.
     */
    @SuppressWarnings("null")
    public boolean shouldSend(UUID deviceId, String sensorKey, String severity) {
        if (!enabled) return true;

        String key = KEY_PREFIX + deviceId + ":" + sensorKey + ":" + severity;
        Duration cooldown = Duration.ofMinutes(cooldownMinutes);
        try {
            Boolean set = redis.opsForValue().setIfAbsent(key, "1", cooldown);
            if (Boolean.TRUE.equals(set)) {
                recordLocalCooldown(key, cooldown);
                return true;
            }
            log.debug("Alert suppressed via Redis (cooldown {}m): device={} sensor={} severity={}",
                    cooldownMinutes, deviceId, sensorKey, severity);
            return false;
        } catch (Exception e) {
            log.warn("AlertDeduplicator: Redis unavailable ({}) — engaging in-memory fallback dedup", e.getMessage());
            return shouldSendFallback(key, cooldown);
        }
    }

    private boolean shouldSendFallback(String key, Duration cooldown) {
        Instant now = Instant.now();
        Instant expiry = localFallbackCache.get(key);
        if (expiry != null && now.isBefore(expiry)) {
            log.debug("Alert suppressed via local fallback cache (cooldown {}m): key={}", cooldownMinutes, key);
            return false;
        }

        recordLocalCooldown(key, cooldown);
        return true;
    }

    private void recordLocalCooldown(String key, Duration cooldown) {
        Instant now = Instant.now();
        localFallbackCache.put(key, now.plus(cooldown));

        // Periodically prune expired entries if map grows
        if (localFallbackCache.size() > 5000) {
            localFallbackCache.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
        }
    }
}
