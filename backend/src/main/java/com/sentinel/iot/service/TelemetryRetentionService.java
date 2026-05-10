package com.sentinel.iot.service;

import com.sentinel.iot.model.TelemetryHourlyAggregate;
import com.sentinel.iot.repository.TelemetryHourlyAggregateRepository;
import com.sentinel.iot.repository.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelemetryRetentionService {

    private final TelemetryRepository telemetryRepository;
    private final TelemetryHourlyAggregateRepository hourlyAggregateRepository;

    @Value("${telemetry.retention-days:30}")
    private int retentionDays;

    /**
     * Runs daily at 02:30 UTC.
     * Phase 1 — aggregate yesterday's raw data into hourly buckets (idempotent upsert).
     * Phase 2 — purge raw rows older than retention-days.
     */
    @Scheduled(cron = "${telemetry.retention.cron:0 30 2 * * *}")
    @Transactional
    public void runRetention() {
        Instant dayStart = LocalDate.now(ZoneOffset.UTC)
                .minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay(ZoneOffset.UTC).toInstant();

        int aggregated = hourlyAggregateRepository.aggregateHourly(dayStart, dayEnd);
        log.info("Retention: aggregated {} hourly buckets for {}",
                aggregated, dayStart.toString().substring(0, 10));

        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = telemetryRepository.deleteByTimestampBefore(cutoff);
        log.info("Retention: deleted {} raw telemetry rows older than {} days", deleted, retentionDays);
    }

    public List<TelemetryHourlyAggregate> getHourlyAggregates(UUID deviceId, Instant from, Instant to) {
        return hourlyAggregateRepository
                .findByDeviceIdAndHourBucketBetweenOrderByHourBucketAsc(deviceId, from, to);
    }
}
