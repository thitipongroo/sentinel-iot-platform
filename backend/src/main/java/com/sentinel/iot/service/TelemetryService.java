package com.sentinel.iot.service;

import com.sentinel.iot.model.Telemetry;
import com.sentinel.iot.repository.TelemetryRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class TelemetryService {

    private final TelemetryRepository telemetryRepository;
    private final RedisService redisService;
    private final Counter telemetryCounter;

    public TelemetryService(TelemetryRepository telemetryRepository,
                            RedisService redisService,
                            MeterRegistry meterRegistry) {
        this.telemetryRepository = telemetryRepository;
        this.redisService = redisService;
        this.telemetryCounter = Counter.builder("sentinel.telemetry.received")
                .description("Total telemetry messages received via MQTT")
                .register(meterRegistry);
    }

    public Telemetry save(UUID deviceId, Double temperature, Double humidity, Boolean motion, Double smokePpm) {
        Telemetry t = new Telemetry(deviceId, temperature, humidity, motion, smokePpm);
        Telemetry saved = telemetryRepository.save(t);
        redisService.setLatestTelemetry(deviceId.toString(), temperature, humidity, motion, smokePpm);
        telemetryCounter.increment();
        return saved;
    }

    public List<Telemetry> getLatest(UUID deviceId, int limit) {
        return telemetryRepository.findByDeviceIdOrderByTimestampDesc(
                deviceId, PageRequest.of(0, limit));
    }

    public List<Telemetry> getRange(UUID deviceId, Instant from, Instant to) {
        return telemetryRepository.findByDeviceIdAndTimestampBetween(deviceId, from, to);
    }

    public long countLastMinute() {
        return telemetryRepository.countByTimestampAfter(Instant.now().minusSeconds(60));
    }
}
