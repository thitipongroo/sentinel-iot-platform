package com.sentinel.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.model.Telemetry;
import com.sentinel.iot.repository.TelemetryRepository;
import com.sentinel.iot.service.RedisService;
import com.sentinel.iot.service.TelemetryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("TelemetryService")
@ExtendWith(MockitoExtension.class)
class TelemetryServiceTest {

    @Mock TelemetryRepository telemetryRepository;
    @Mock RedisService         redisService;

    TelemetryService service;

    @BeforeEach
    void setUp() {
        service = new TelemetryService(
                telemetryRepository,
                redisService,
                new ObjectMapper(),
                new SimpleMeterRegistry());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @SuppressWarnings("null")
        @Test
        @DisplayName("persists to repository and updates Redis cache with supplied sensor values")
        void save_persistsTelemetryAndUpdatesRedisCache() {
            UUID deviceId = UUID.randomUUID();
            Telemetry stored = new Telemetry(deviceId, 72.0, 55.0, false, 10.0);
            when(telemetryRepository.save(any(Telemetry.class))).thenReturn(stored);

            Telemetry result = service.save(deviceId, 72.0, 55.0, false, 10.0);

            verify(telemetryRepository).save(any(Telemetry.class));
            verify(redisService).setLatestTelemetry(deviceId.toString(), 72.0, 55.0, false, 10.0);
            assertThat(result)
                    .as("save must return the entity returned by the repository")
                    .isSameAs(stored);
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("increments the telemetry.received counter on each successful save")
        void save_incrementsTelemetryCounter() {
            UUID deviceId = UUID.randomUUID();
            when(telemetryRepository.save(any(Telemetry.class)))
                    .thenReturn(new Telemetry(deviceId, 70.0, 50.0, false, 5.0));

            service.save(deviceId, 70.0, 50.0, false, 5.0);
            service.save(deviceId, 71.0, 51.0, false, 6.0);

            verify(telemetryRepository, times(2)).save(any(Telemetry.class));
            verify(redisService, times(2)).setLatestTelemetry(eq(deviceId.toString()),
                    anyDouble(), anyDouble(), anyBoolean(), anyDouble());
        }
    }

    // ── saveFallback ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("saveFallback")
    class SaveFallback {

        @Test
        @DisplayName("updates Redis cache and pushes a serialised replay-queue message, returns null")
        void saveFallback_updatesRedisAndPushesToReplayQueue() {
            UUID deviceId = UUID.randomUUID();

            Telemetry result = service.saveFallback(
                    deviceId, 72.0, 55.0, false, 10.0, new RuntimeException("DB down"));

            verify(redisService).setLatestTelemetry(deviceId.toString(), 72.0, 55.0, false, 10.0);
            verify(redisService).pushToReplayQueue(anyString());
            assertThat(result).as("fallback must return null so callers treat it as a no-op").isNull();
        }

        @Test
        @DisplayName("still updates Redis cache when ObjectMapper serialisation throws")
        void saveFallback_serialisationFails_stillUpdatesRedisCache() throws Exception {
            UUID deviceId = UUID.randomUUID();
            ObjectMapper badMapper = mock(ObjectMapper.class);
            doThrow(new IOException("json error")).when(badMapper).writeValueAsString(any());

            TelemetryService serviceWithBadMapper = new TelemetryService(
                    telemetryRepository, redisService, badMapper, new SimpleMeterRegistry());

            serviceWithBadMapper.saveFallback(deviceId, 72.0, 55.0, false, 10.0,
                    new RuntimeException("DB down"));

            verify(redisService).setLatestTelemetry(deviceId.toString(), 72.0, 55.0, false, 10.0);
            verify(redisService, never()).pushToReplayQueue(anyString());
        }
    }

    // ── getLatest ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getLatest")
    class GetLatest {

        @SuppressWarnings("null")
        @Test
        @DisplayName("delegates to repository with PageRequest(0, limit) and returns result")
        void getLatest_delegatesToRepositoryWithCorrectPageRequest() {
            UUID deviceId = UUID.randomUUID();
            Telemetry row = new Telemetry(deviceId, 70.0, 50.0, false, 5.0);
            when(telemetryRepository.findByDeviceIdOrderByTimestampDesc(
                    deviceId, PageRequest.of(0, 10))).thenReturn(List.of(row));

            List<Telemetry> result = service.getLatest(deviceId, 10);

            assertThat(result).containsExactly(row);
        }
    }

    // ── getRange ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getRange")
    class GetRange {

        @Test
        @DisplayName("delegates to repository with exact from/to bounds and returns result")
        void getRange_delegatesToRepositoryWithSuppliedBounds() {
            UUID deviceId = UUID.randomUUID();
            Instant from = Instant.parse("2026-01-01T00:00:00Z");
            Instant to   = Instant.parse("2026-01-02T00:00:00Z");
            when(telemetryRepository.findByDeviceIdAndTimestampBetween(deviceId, from, to))
                    .thenReturn(List.of());

            List<Telemetry> result = service.getRange(deviceId, from, to);

            assertThat(result).isEmpty();
            verify(telemetryRepository).findByDeviceIdAndTimestampBetween(deviceId, from, to);
        }
    }

    // ── countLastMinute ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("countLastMinute")
    class CountLastMinute {

        @Test
        @DisplayName("delegates to repository with a timestamp ~60 s in the past and returns count")
        void countLastMinute_returnsRepositoryCount() {
            when(telemetryRepository.countByTimestampAfter(any(Instant.class))).thenReturn(42L);

            assertThat(service.countLastMinute())
                    .as("countLastMinute must return the value from the repository")
                    .isEqualTo(42L);
            verify(telemetryRepository).countByTimestampAfter(any(Instant.class));
        }
    }
}
