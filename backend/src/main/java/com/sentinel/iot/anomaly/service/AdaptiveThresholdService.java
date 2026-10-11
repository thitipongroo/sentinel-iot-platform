package com.sentinel.iot.anomaly.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.anomaly.dto.AdaptiveThresholdConfig;
import com.sentinel.iot.anomaly.dto.DynamicBaseline;
import com.sentinel.iot.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptive Dynamic Alarm Threshold Engine.
 *
 * <p>Computes rolling statistical baseline bands [μ - kσ, μ + kσ] per device and sensor key.
 * Features:
 * <ul>
 *   <li>Online Welford/EMA baseline learning rate adjustment</li>
 *   <li>Outlier dampening to protect baselines against contamination</li>
 *   <li>Distributed Redis state synchronization across cluster nodes</li>
 *   <li>Strict multi-tenant isolation</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class AdaptiveThresholdService {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    private static final String REDIS_PREFIX = "sentinel:baseline:";
    private static final Duration BASELINE_TTL = Duration.ofDays(14);

    // In-memory L1 cache for sub-microsecond local access: key = "orgId:deviceId:sensorKey"
    private final Map<String, DynamicBaseline> localCache = new ConcurrentHashMap<>();

    // Global default configuration
    private final AdaptiveThresholdConfig defaultConfig = AdaptiveThresholdConfig.defaults();

    @Value("${anomaly.adaptive.min-sigma-floor:0.2}")
    private double minSigmaFloor;

    /**
     * Updates rolling statistical baseline and evaluates whether the observed value breaches dynamic bounds.
     */
    public DynamicBaseline updateAndEvaluate(UUID orgId, UUID deviceId, String sensorKey, double value) {
        return updateAndEvaluate(orgId, deviceId, sensorKey, value, defaultConfig);
    }

    /**
     * Updates rolling statistical baseline with custom configuration and returns evaluated DynamicBaseline.
     */
    public DynamicBaseline updateAndEvaluate(UUID orgId, UUID deviceId, String sensorKey, double value,
                                             AdaptiveThresholdConfig config) {
        UUID effectiveOrgId = orgId != null ? orgId : TenantContext.get();
        String compositeKey = cacheKey(effectiveOrgId, deviceId, sensorKey);

        DynamicBaseline current = getBaseline(effectiveOrgId, deviceId, sensorKey).orElse(null);

        if (current == null) {
            // First observation
            DynamicBaseline initial = DynamicBaseline.builder()
                    .sensorKey(sensorKey)
                    .mean(value)
                    .stdDev(Math.max(1.0, minSigmaFloor))
                    .lowerBand(value - config.getSigmaMultiplier() * Math.max(1.0, minSigmaFloor))
                    .upperBand(value + config.getSigmaMultiplier() * Math.max(1.0, minSigmaFloor))
                    .sampleCount(1)
                    .lastObservedValue(value)
                    .zScore(0.0)
                    .breached(false)
                    .lastUpdatedAt(Instant.now())
                    .build();

            saveBaseline(effectiveOrgId, deviceId, sensorKey, initial);
            return initial;
        }

        long count = current.getSampleCount() + 1;
        double mean = current.getMean();
        double variance = Math.pow(current.getStdDev(), 2);

        double stdDev = Math.max(current.getStdDev(), minSigmaFloor);
        double zScore = (value - mean) / stdDev;
        double absZ = Math.abs(zScore);

        boolean isWarmup = count < config.getMinSamples();
        boolean breached = !isWarmup && (absZ > config.getSigmaMultiplier());

        // Baseline protection: downweight extreme anomalies so they don't corrupt the normal baseline
        double alpha = config.getAlpha();
        if (breached && absZ > 4.0) {
            alpha *= config.getAnomalyDampeningFactor();
        }

        // Rolling EMA update
        double newMean = (1.0 - alpha) * mean + (alpha * value);
        double newVariance = (1.0 - alpha) * variance + (alpha * Math.pow(value - newMean, 2));
        double newStdDev = Math.max(Math.sqrt(newVariance), minSigmaFloor);

        double lowerBand = newMean - (config.getSigmaMultiplier() * newStdDev);
        double upperBand = newMean + (config.getSigmaMultiplier() * newStdDev);

        DynamicBaseline updated = DynamicBaseline.builder()
                .sensorKey(sensorKey)
                .mean(Math.round(newMean * 1000.0) / 1000.0)
                .stdDev(Math.round(newStdDev * 1000.0) / 1000.0)
                .lowerBand(Math.round(lowerBand * 1000.0) / 1000.0)
                .upperBand(Math.round(upperBand * 1000.0) / 1000.0)
                .sampleCount(count)
                .lastObservedValue(value)
                .zScore(Math.round(zScore * 100.0) / 100.0)
                .breached(breached)
                .lastUpdatedAt(Instant.now())
                .build();

        saveBaseline(effectiveOrgId, deviceId, sensorKey, updated);
        return updated;
    }

    /**
     * Retrieves dynamic baseline for a specific sensor.
     */
    public Optional<DynamicBaseline> getBaseline(UUID orgId, UUID deviceId, String sensorKey) {
        UUID effectiveOrgId = orgId != null ? orgId : TenantContext.get();
        String cKey = cacheKey(effectiveOrgId, deviceId, sensorKey);

        DynamicBaseline cached = localCache.get(cKey);
        if (cached != null) {
            return Optional.of(cached);
        }

        String redisKey = redisKey(effectiveOrgId, deviceId, sensorKey);
        String json = redis.opsForValue().get(redisKey);
        if (json != null) {
            try {
                DynamicBaseline baseline = objectMapper.readValue(json, DynamicBaseline.class);
                localCache.put(cKey, baseline);
                return Optional.of(baseline);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize DynamicBaseline from Redis key {}: {}", redisKey, e.getMessage());
            }
        }

        return Optional.empty();
    }

    /**
     * Retrieves all cached dynamic baselines for a given device across all sensors.
     */
    public Map<String, DynamicBaseline> getBaselinesForDevice(UUID orgId, UUID deviceId) {
        UUID effectiveOrgId = orgId != null ? orgId : TenantContext.get();
        Map<String, DynamicBaseline> results = new HashMap<>();

        // Check local cache
        String prefix = (effectiveOrgId != null ? effectiveOrgId.toString() : "null") + ":" + deviceId + ":";
        localCache.forEach((k, v) -> {
            if (k.startsWith(prefix)) {
                String sensor = k.substring(prefix.length());
                results.put(sensor, v);
            }
        });

        // Scan Redis keys if local cache doesn't have it
        String pattern = REDIS_PREFIX + (effectiveOrgId != null ? effectiveOrgId : "*") + ":" + deviceId + ":*";
        try {
            Set<String> keys = redis.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                for (String key : keys) {
                    String sensor = key.substring(key.lastIndexOf(':') + 1);
                    if (!results.containsKey(sensor)) {
                        String json = redis.opsForValue().get(key);
                        if (json != null) {
                            try {
                                results.put(sensor, objectMapper.readValue(json, DynamicBaseline.class));
                            } catch (JsonProcessingException ignored) {}
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error scanning Redis keys for device baselines: {}", e.getMessage());
        }

        return results;
    }

    /**
     * Resets baselines for all sensors on a device (e.g. after maintenance or calibration).
     */
    public void resetBaselines(UUID orgId, UUID deviceId) {
        UUID effectiveOrgId = orgId != null ? orgId : TenantContext.get();
        String prefix = (effectiveOrgId != null ? effectiveOrgId.toString() : "null") + ":" + deviceId + ":";
        localCache.keySet().removeIf(k -> k.startsWith(prefix));

        String pattern = REDIS_PREFIX + (effectiveOrgId != null ? effectiveOrgId : "*") + ":" + deviceId + ":*";
        try {
            Set<String> keys = redis.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Error resetting baselines in Redis: {}", e.getMessage());
        }
        log.info("Reset dynamic baselines for device={}", deviceId);
    }

    /**
     * Resets baseline for a single sensor on a device.
     */
    public void resetBaseline(UUID orgId, UUID deviceId, String sensorKey) {
        UUID effectiveOrgId = orgId != null ? orgId : TenantContext.get();
        localCache.remove(cacheKey(effectiveOrgId, deviceId, sensorKey));
        redis.delete(redisKey(effectiveOrgId, deviceId, sensorKey));
        log.info("Reset dynamic baseline for device={} sensor={}", deviceId, sensorKey);
    }

    private void saveBaseline(UUID orgId, UUID deviceId, String sensorKey, DynamicBaseline baseline) {
        String cKey = cacheKey(orgId, deviceId, sensorKey);
        localCache.put(cKey, baseline);

        String rKey = redisKey(orgId, deviceId, sensorKey);
        try {
            String json = objectMapper.writeValueAsString(baseline);
            redis.opsForValue().set(rKey, json, BASELINE_TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize DynamicBaseline for key {}: {}", rKey, e.getMessage());
        }
    }

    private String cacheKey(UUID orgId, UUID deviceId, String sensorKey) {
        return (orgId != null ? orgId.toString() : "null") + ":" + deviceId + ":" + sensorKey;
    }

    private String redisKey(UUID orgId, UUID deviceId, String sensorKey) {
        return REDIS_PREFIX + (orgId != null ? orgId.toString() : "global") + ":" + deviceId + ":" + sensorKey;
    }
}
