package com.sentinel.iot;

import com.sentinel.iot.model.TelemetryHourlyAggregate;
import com.sentinel.iot.repository.TelemetryHourlyAggregateRepository;
import com.sentinel.iot.repository.TelemetryRepository;
import com.sentinel.iot.service.TelemetryRetentionService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryRetentionServiceTest {

    @Mock TelemetryRepository telemetryRepository;
    @Mock TelemetryHourlyAggregateRepository hourlyAggregateRepository;
    @Mock EntityManager entityManager;

    TelemetryRetentionService service;

    @SuppressWarnings("null")
    @BeforeEach
    void setUp() {
        service = new TelemetryRetentionService(telemetryRepository, hourlyAggregateRepository);
        ReflectionTestUtils.setField(service, "entityManager",          entityManager);
        ReflectionTestUtils.setField(service, "retentionDays",          30);
        ReflectionTestUtils.setField(service, "lateArrivalLookbackDays", 2);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    @SuppressWarnings("null")
    private Query stubPartitionList(List<String> partitions) {
        Query listQuery = mock(Query.class);
        when(listQuery.getResultList()).thenReturn(partitions);
        when(entityManager.createNativeQuery(contains("pg_inherits"))).thenReturn(listQuery);
        return listQuery;
    }

    // ── phase 1: aggregation ─────────────────────────────────────────────────

    @SuppressWarnings("null")
    @Test
    void runRetention_callsAggregateHourly_withLookbackWindow() {
        stubPartitionList(List.of());
        when(hourlyAggregateRepository.aggregateHourly(any(), any())).thenReturn(5);

        service.runRetention();

        verify(hourlyAggregateRepository).aggregateHourly(
                argThat(from -> from.isBefore(Instant.now())),
                argThat(to   -> to.isBefore(Instant.now().plusSeconds(5))));
    }

    // ── phase 2: purge ───────────────────────────────────────────────────────

    @SuppressWarnings("null")
    @Test
    void runRetention_callsDeleteByTimestampBefore_withRetentionCutoff() {
        stubPartitionList(List.of());
        when(hourlyAggregateRepository.aggregateHourly(any(), any())).thenReturn(0);
        when(telemetryRepository.deleteByTimestampBefore(any())).thenReturn(10);

        Instant before = Instant.now();
        service.runRetention();
        Instant after  = Instant.now();

        // Cutoff should be ~30 days ago
        verify(telemetryRepository).deleteByTimestampBefore(argThat(cutoff ->
                cutoff.isAfter(before.minus(31, ChronoUnit.DAYS)) &&
                cutoff.isBefore(after.minus(29, ChronoUnit.DAYS))));
    }

    // ── phase 3: partition management ────────────────────────────────────────

    @SuppressWarnings("null")
    @Test
    void runRetention_noPartitions_skipsDropPhase() {
        stubPartitionList(List.of());
        when(hourlyAggregateRepository.aggregateHourly(any(), any())).thenReturn(0);

        service.runRetention();

        // Only the pg_inherits list query — no COUNT/DETACH/DROP
        verify(entityManager, times(1)).createNativeQuery(anyString());
    }

    @SuppressWarnings("null")
    @Test
    void runRetention_skipsPartitionWithUnrecognisedName() {
        stubPartitionList(List.of("telemetry_not_a_date"));
        when(hourlyAggregateRepository.aggregateHourly(any(), any())).thenReturn(0);

        service.runRetention();

        // Only the pg_inherits list query — no COUNT or DROP for unrecognised partition name
        verify(entityManager, times(1)).createNativeQuery(anyString());
    }

    @SuppressWarnings("null")
    @Test
    void runRetention_dropsEmptyExpiredPartition() {
        String partition = "telemetry_2020_01"; // well outside 30-day retention window

        stubPartitionList(List.of(partition));

        Query countQuery  = mock(Query.class);
        Query detachQuery = mock(Query.class);
        Query dropQuery   = mock(Query.class);
        when(countQuery.getSingleResult()).thenReturn(0L);
        when(entityManager.createNativeQuery(contains("COUNT"))).thenReturn(countQuery);
        when(entityManager.createNativeQuery(contains("DETACH"))).thenReturn(detachQuery);
        when(entityManager.createNativeQuery(contains("DROP TABLE"))).thenReturn(dropQuery);
        when(hourlyAggregateRepository.aggregateHourly(any(), any())).thenReturn(0);

        service.runRetention();

        verify(detachQuery).executeUpdate();
        verify(dropQuery).executeUpdate();
    }

    @SuppressWarnings("null")
    @Test
    void runRetention_skipsNonEmptyExpiredPartition() {
        String partition = "telemetry_2020_01";

        stubPartitionList(List.of(partition));

        Query countQuery = mock(Query.class);
        when(countQuery.getSingleResult()).thenReturn(5L); // still has rows
        when(entityManager.createNativeQuery(contains("COUNT"))).thenReturn(countQuery);
        when(hourlyAggregateRepository.aggregateHourly(any(), any())).thenReturn(0);

        service.runRetention();

        verify(entityManager, never()).createNativeQuery(contains("DETACH"));
        verify(entityManager, never()).createNativeQuery(contains("DROP TABLE"));
    }

    // ── getHourlyAggregates ──────────────────────────────────────────────────

    @SuppressWarnings("null")
    @Test
    void getHourlyAggregates_delegatesToRepository() {
        UUID deviceId = UUID.randomUUID();
        Instant from  = Instant.now().minus(24, ChronoUnit.HOURS);
        Instant to    = Instant.now();
        TelemetryHourlyAggregate agg = new TelemetryHourlyAggregate();
        when(hourlyAggregateRepository.findByDeviceIdAndHourBucketBetweenOrderByHourBucketAsc(deviceId, from, to))
                .thenReturn(List.of(agg));

        List<TelemetryHourlyAggregate> result = service.getHourlyAggregates(deviceId, from, to);

        assertThat(result).containsExactly(agg);
    }
}
