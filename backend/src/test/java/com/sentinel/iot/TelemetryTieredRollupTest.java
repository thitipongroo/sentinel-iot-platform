package com.sentinel.iot;

import com.sentinel.iot.model.TelemetryDailyRollup;
import com.sentinel.iot.model.TelemetryMonthlyRollup;
import com.sentinel.iot.repository.TelemetryDailyRollupRepository;
import com.sentinel.iot.repository.TelemetryHourlyAggregateRepository;
import com.sentinel.iot.repository.TelemetryMonthlyRollupRepository;
import com.sentinel.iot.repository.TelemetryRepository;
import com.sentinel.iot.service.TelemetryArchivalService;
import com.sentinel.iot.service.TelemetryRetentionService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("TelemetryTieredRollupTest")
@ExtendWith(MockitoExtension.class)
class TelemetryTieredRollupTest {

    @Mock
    private TelemetryRepository telemetryRepository;

    @Mock
    private TelemetryHourlyAggregateRepository hourlyAggregateRepository;

    @Mock
    private TelemetryDailyRollupRepository dailyRollupRepository;

    @Mock
    private TelemetryMonthlyRollupRepository monthlyRollupRepository;

    @Mock
    private TelemetryArchivalService archivalService;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private TelemetryRetentionService retentionService;

    private UUID deviceId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        deviceId = UUID.randomUUID();
        orgId = UUID.randomUUID();

        ReflectionTestUtils.setField(retentionService, "entityManager", entityManager);
        ReflectionTestUtils.setField(retentionService, "retentionDays", 30);
        ReflectionTestUtils.setField(retentionService, "lateArrivalLookbackDays", 2);
    }

    @Test
    @DisplayName("runRetention: executes daily and monthly rollups alongside hourly aggregation")
    void runRetention_triggersTieredRollups() {
        when(hourlyAggregateRepository.aggregateHourly(any(), any())).thenReturn(24);
        when(dailyRollupRepository.aggregateDaily(any(), any())).thenReturn(1);
        when(monthlyRollupRepository.aggregateMonthly(any(), any())).thenReturn(1);
        when(telemetryRepository.deleteByTimestampBefore(any())).thenReturn(0);

        var emptyQuery = mock(jakarta.persistence.Query.class);
        when(emptyQuery.getResultList()).thenReturn(Collections.emptyList());
        when(entityManager.createNativeQuery(anyString())).thenReturn(emptyQuery);

        retentionService.runRetention();

        verify(hourlyAggregateRepository).aggregateHourly(any(), any());
        verify(dailyRollupRepository).aggregateDaily(any(), any());
        verify(monthlyRollupRepository).aggregateMonthly(any(), any());
        verify(archivalService).archiveRange(any(), any());
    }

    @Test
    @DisplayName("getDailyRollups: returns daily rollups from repository")
    void getDailyRollups_returnsRecords() {
        Instant now = Instant.now();
        Instant from = now.minus(7, ChronoUnit.DAYS);
        TelemetryDailyRollup rollup = TelemetryDailyRollup.builder()
                .id(UUID.randomUUID())
                .deviceId(deviceId)
                .organizationId(orgId)
                .dayBucket(now)
                .tempAvg(24.5)
                .tempMin(20.0)
                .tempMax(30.0)
                .humAvg(65.0)
                .humMin(50.0)
                .humMax(80.0)
                .sampleCount(1440)
                .build();

        when(dailyRollupRepository.findByDeviceIdAndDayBucketBetweenOrderByDayBucketAsc(deviceId, from, now))
                .thenReturn(List.of(rollup));

        List<TelemetryDailyRollup> result = retentionService.getDailyRollups(deviceId, from, now);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTempAvg()).isEqualTo(24.5);
        assertThat(result.get(0).getSampleCount()).isEqualTo(1440);
        verify(dailyRollupRepository).findByDeviceIdAndDayBucketBetweenOrderByDayBucketAsc(deviceId, from, now);
    }

    @Test
    @DisplayName("getMonthlyRollups: returns monthly rollups from repository")
    void getMonthlyRollups_returnsRecords() {
        Instant now = Instant.now();
        Instant from = now.minus(365, ChronoUnit.DAYS);
        TelemetryMonthlyRollup rollup = TelemetryMonthlyRollup.builder()
                .id(UUID.randomUUID())
                .deviceId(deviceId)
                .organizationId(orgId)
                .monthBucket(now)
                .tempAvg(26.2)
                .tempMin(18.0)
                .tempMax(35.5)
                .sampleCount(43200)
                .build();

        when(monthlyRollupRepository.findByDeviceIdAndMonthBucketBetweenOrderByMonthBucketAsc(deviceId, from, now))
                .thenReturn(List.of(rollup));

        List<TelemetryMonthlyRollup> result = retentionService.getMonthlyRollups(deviceId, from, now);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTempAvg()).isEqualTo(26.2);
        assertThat(result.get(0).getSampleCount()).isEqualTo(43200);
        verify(monthlyRollupRepository).findByDeviceIdAndMonthBucketBetweenOrderByMonthBucketAsc(deviceId, from, now);
    }
}
