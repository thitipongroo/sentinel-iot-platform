package com.sentinel.iot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final StringRedisTemplate redis;

    private static final String STATUS_PREFIX = "device:status:";
    private static final String TELEMETRY_PREFIX = "device:telemetry:";
    private static final Duration TTL = Duration.ofMinutes(10);

    public void setDeviceStatus(String deviceId, String status) {
        redis.opsForValue().set(STATUS_PREFIX + deviceId, status, TTL);
    }

    public String getDeviceStatus(String deviceId) {
        return redis.opsForValue().get(STATUS_PREFIX + deviceId);
    }

    public void setLatestTelemetry(String deviceId, double temperature, double humidity) {
        Map<String, String> fields = new HashMap<>();
        fields.put("temperature", String.valueOf(temperature));
        fields.put("humidity", String.valueOf(humidity));
        fields.put("ts", String.valueOf(System.currentTimeMillis()));
        redis.opsForHash().putAll(TELEMETRY_PREFIX + deviceId, fields);
        redis.expire(TELEMETRY_PREFIX + deviceId, TTL);
    }

    public Map<Object, Object> getLatestTelemetry(String deviceId) {
        return redis.opsForHash().entries(TELEMETRY_PREFIX + deviceId);
    }
}
