package com.sentinel.iot.service;

import com.sentinel.iot.model.TelemetryHourlyAggregate;
import com.sentinel.iot.repository.TelemetryHourlyAggregateRepository;
import com.sentinel.iot.repository.TelemetryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelemetryRetentionService {

    private final TelemetryRepository telemetryRepository;
    private final TelemetryHourlyAggregateRepository hourlyAggregateRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${telemetry.retention-days:30}")
    private int retentionDays;

    private static final DateTimeFormatter PARTITION_FMT = DateTimeFormatter.ofPattern("yyyy_MM");

    /**
     * Runs daily at 02:30 UTC.
     * Phase 1 — aggregate yesterday's raw data into hourly buckets (idempotent upsert).
     * Phase 2 — purge raw rows older than retention-days.
     * Phase 3 — DETACH and DROP empty monthly partition tables whose data has been fully pruned.
     */
    @Scheduled(cron = "${telemetry.retention.cron:0 30 2 * * *}")
    @Transactional
    public void runRetention() {
        Instant dayStart = LocalDate.now(ZoneOffset.UTC)
                .minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay(ZoneOffset.UTC).toInstant();

        // Phase 1: aggregate
        int aggregated = hourlyAggregateRepository.aggregateHourly(dayStart, dayEnd);
        log.info("Retention: aggregated {} hourly buckets for {}",
                aggregated, dayStart.toString().substring(0, 10));

        // Phase 2: prune raw rows
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = telemetryRepository.deleteByTimestampBefore(cutoff);
        log.info("Retention: deleted {} raw telemetry rows older than {} days", deleted, retentionDays);

        // Phase 3: drop empty partition tables that are fully outside the retention window
        dropEmptyExpiredPartitions(cutoff);
    }

    /**
     * Detaches and drops monthly child partition tables that:
     * 1. End before the retention cutoff (all their data has been pruned), AND
     * 2. Contain zero rows (confirmed empty — guards against late-arriving or backfill data).
     *
     * The DEFAULT partition is never dropped.
     */
    private void dropEmptyExpiredPartitions(Instant cutoff) {
        YearMonth cutoffMonth = YearMonth.from(cutoff.atZone(ZoneOffset.UTC));

        @SuppressWarnings("unchecked")
        List<String> partitionNames = entityManager.createNativeQuery(
                "SELECT child.relname " +
                "FROM pg_inherits " +
                "JOIN pg_class parent ON pg_inherits.inhparent = parent.oid " +
                "JOIN pg_class child  ON pg_inherits.inhrelid  = child.oid " +
                "WHERE parent.relname = 'telemetry' " +
                "  AND child.relname LIKE 'telemetry\\_%\\_\\_%' ESCAPE '\\' " +
                "  AND child.relname != 'telemetry_default'"
        ).getResultList();

        for (String partition : partitionNames) {
            // Extract year_month suffix, e.g. "telemetry_2025_01" → "2025_01"
            String suffix = partition.substring("telemetry_".length());
            YearMonth partitionMonth;
            try {
                partitionMonth = YearMonth.parse(suffix, PARTITION_FMT);
            } catch (Exception e) {
                log.debug("Skipping unrecognised partition name: {}", partition);
                continue;
            }

            // Only consider months that ended before the retention cutoff
            if (!partitionMonth.isBefore(cutoffMonth)) continue;

            // Verify the partition is truly empty before dropping
            Long rowCount = (Long) entityManager
                    .createNativeQuery("SELECT COUNT(*) FROM " + partition)
                    .getSingleResult();
            if (rowCount != null && rowCount > 0) {
                log.debug("Retention: partition {} has {} rows — skipping drop", partition, rowCount);
                continue;
            }

            try {
                entityManager.createNativeQuery(
                        "ALTER TABLE telemetry DETACH PARTITION " + partition
                ).executeUpdate();
                entityManager.createNativeQuery("DROP TABLE " + partition).executeUpdate();
                log.info("Retention: dropped empty expired partition {}", partition);
            } catch (Exception e) {
                log.error("Retention: failed to drop partition {}: {}", partition, e.getMessage());
            }
        }
    }

    public List<TelemetryHourlyAggregate> getHourlyAggregates(UUID deviceId, Instant from, Instant to) {
        return hourlyAggregateRepository
                .findByDeviceIdAndHourBucketBetweenOrderByHourBucketAsc(deviceId, from, to);
    }
}
