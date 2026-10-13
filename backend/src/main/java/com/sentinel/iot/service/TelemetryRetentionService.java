package com.sentinel.iot.service;

import com.sentinel.iot.model.TelemetryDailyRollup;
import com.sentinel.iot.model.TelemetryHourlyAggregate;
import com.sentinel.iot.model.TelemetryMonthlyRollup;
import com.sentinel.iot.repository.TelemetryDailyRollupRepository;
import com.sentinel.iot.repository.TelemetryHourlyAggregateRepository;
import com.sentinel.iot.repository.TelemetryMonthlyRollupRepository;
import com.sentinel.iot.repository.TelemetryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class TelemetryRetentionService {

    private final TelemetryRepository telemetryRepository;
    private final TelemetryHourlyAggregateRepository hourlyAggregateRepository;
    private final TelemetryDailyRollupRepository dailyRollupRepository;
    private final TelemetryMonthlyRollupRepository monthlyRollupRepository;
    private final TelemetryArchivalService archivalService;
    private final StringRedisTemplate redisTemplate;

    public TelemetryRetentionService(TelemetryRepository telemetryRepository,
                                     TelemetryHourlyAggregateRepository hourlyAggregateRepository) {
        this(telemetryRepository, hourlyAggregateRepository, null, null, null, null);
    }

    @Autowired
    public TelemetryRetentionService(TelemetryRepository telemetryRepository,
                                     TelemetryHourlyAggregateRepository hourlyAggregateRepository,
                                     @Autowired(required = false) TelemetryDailyRollupRepository dailyRollupRepository,
                                     @Autowired(required = false) TelemetryMonthlyRollupRepository monthlyRollupRepository,
                                     @Autowired(required = false) TelemetryArchivalService archivalService,
                                     @Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.telemetryRepository = telemetryRepository;
        this.hourlyAggregateRepository = hourlyAggregateRepository;
        this.dailyRollupRepository = dailyRollupRepository;
        this.monthlyRollupRepository = monthlyRollupRepository;
        this.archivalService = archivalService;
        this.redisTemplate = redisTemplate;
    }

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${telemetry.retention-days:30}")
    private int retentionDays;

    @Value("${telemetry.partition.months-ahead:6}")
    private int proactiveMonths;

    /**
     * Look-back window for late-arrival aggregation recomputation.
     */
    @Value("${telemetry.late-arrival.lookback-days:2}")
    private int lateArrivalLookbackDays;

    private static final DateTimeFormatter PARTITION_FMT = DateTimeFormatter.ofPattern("yyyy_MM");

    /**
     * Runs daily at 02:30 UTC.
     * Phase 1 — aggregate hourly, daily, and monthly rollups (idempotent upserts).
     * Phase 2 — archive raw rows to columnar Parquet cold storage before purging (zero data loss).
     * Phase 3 — purge raw rows older than retention-days.
     * Phase 4 — DETACH and DROP empty monthly partition tables whose data has been archived and pruned.
     */
    @Scheduled(cron = "${telemetry.retention.cron:0 30 2 * * *}")
    @Transactional
    public void runRetention() {
        if (redisTemplate != null) {
            try {
                Boolean acquired = redisTemplate.opsForValue()
                        .setIfAbsent("lock:telemetry:retention", "locked", Duration.ofHours(2));
                if (Boolean.FALSE.equals(acquired)) {
                    log.info("Retention: another replica is already executing the daily retention job. Skipping.");
                    return;
                }
            } catch (Exception e) {
                log.warn("Redis distributed lock check failed: {}. Continuing with retention execution.", e.getMessage());
            }
        }

        // Phase 1: Tiered Rollup Aggregations (Hourly, Daily, Monthly)
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate lookbackStart = today.minusDays(Math.max(1, lateArrivalLookbackDays));
        Instant aggregateFrom = lookbackStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant aggregateTo   = today.atStartOfDay(ZoneOffset.UTC).toInstant();

        int aggregatedHourly = hourlyAggregateRepository.aggregateHourly(aggregateFrom, aggregateTo);
        log.info("Retention: aggregated {} hourly buckets for window [{}, {}] (lookback={}d)",
                aggregatedHourly, lookbackStart, today.minusDays(1), lateArrivalLookbackDays);

        if (dailyRollupRepository != null) {
            try {
                int aggregatedDaily = dailyRollupRepository.aggregateDaily(aggregateFrom, aggregateTo);
                log.info("Retention: aggregated {} daily rollups for window [{}, {}]", aggregatedDaily, lookbackStart, today);
            } catch (Exception e) {
                log.warn("Failed to compute daily rollups: {}", e.getMessage());
            }
        }

        if (monthlyRollupRepository != null) {
            try {
                // Monthly rollups cover from start of previous month to current month
                Instant monthFrom = today.minusMonths(1).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                int aggregatedMonthly = monthlyRollupRepository.aggregateMonthly(monthFrom, aggregateTo);
                log.info("Retention: aggregated {} monthly rollups for window [{}, {}]", aggregatedMonthly, monthFrom, aggregateTo);
            } catch (Exception e) {
                log.warn("Failed to compute monthly rollups: {}", e.getMessage());
            }
        }

        // Phase 2: Automated Parquet Archival before pruning (Zero Data Loss)
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        if (archivalService != null) {
            try {
                // Archive from 60 days ago up to cutoff to catch expiring data
                Instant archiveStart = cutoff.minus(30, ChronoUnit.DAYS);
                var report = archivalService.archiveRange(archiveStart, cutoff);
                log.info("Retention: cold archival complete — {} records ({} bytes) in {}ms",
                        report.recordsArchived(), report.bytesWritten(), report.durationMs());
            } catch (Exception e) {
                log.error("Retention: automated Parquet archival failed: {}", e.getMessage());
            }
        }

        // Phase 3: Prune raw rows
        int deleted = telemetryRepository.deleteByTimestampBefore(cutoff);
        log.info("Retention: deleted {} raw telemetry rows older than {} days", deleted, retentionDays);

        // Phase 4: drop empty partition tables that are fully outside the retention window
        dropEmptyExpiredPartitions(cutoff);
    }

    /**
     * Proactively provisions upcoming monthly partition tables N months into the future.
     * Prevents inserts from falling into telemetry_default, maintaining O(1) partition
     * pruning performance and eliminating manual database migration maintenance.
     * Runs on the 1st of every month at 03:00 UTC (and can be invoked programmatically).
     */
    @Scheduled(cron = "${telemetry.partition.cron:0 0 3 1 * *}")
    @Transactional
    public void ensureUpcomingPartitions() {
        if (redisTemplate != null) {
            try {
                Boolean acquired = redisTemplate.opsForValue()
                        .setIfAbsent("lock:telemetry:partition-provision", "locked", Duration.ofHours(2));
                if (Boolean.FALSE.equals(acquired)) {
                    log.info("Auto-partition: another replica is already provisioning upcoming partitions. Skipping.");
                    return;
                }
            } catch (Exception e) {
                log.warn("Redis distributed lock check failed: {}. Continuing with partition provisioning.", e.getMessage());
            }
        }
        createPartitionsAhead(proactiveMonths);
    }

    public void createPartitionsAhead(int months) {
        YearMonth current = YearMonth.now(ZoneOffset.UTC);
        for (int i = 0; i <= months; i++) {
            YearMonth targetMonth = current.plusMonths(i);
            String partitionName = "telemetry_" + targetMonth.format(PARTITION_FMT);
            try {
                @SuppressWarnings("unchecked")
                List<?> exists = entityManager.createNativeQuery(
                        "SELECT child.relname FROM pg_inherits " +
                        "JOIN pg_class parent ON pg_inherits.inhparent = parent.oid " +
                        "JOIN pg_class child ON pg_inherits.inhrelid = child.oid " +
                        "WHERE parent.relname = 'telemetry' AND child.relname = :pname"
                ).setParameter("pname", partitionName).getResultList();

                if (exists.isEmpty()) {
                    LocalDate start = targetMonth.atDay(1);
                    LocalDate end = targetMonth.plusMonths(1).atDay(1);
                    entityManager.createNativeQuery(
                            "CREATE TABLE IF NOT EXISTS " + partitionName +
                            " PARTITION OF telemetry FOR VALUES FROM ('" + start + "') TO ('" + end + "')"
                    ).executeUpdate();
                    log.info("Auto-partition: created upcoming partition {} for range [{}, {})", partitionName, start, end);
                }
            } catch (Exception e) {
                log.warn("Auto-partition: failed to create partition {}: {}", partitionName, e.getMessage());
            }
        }
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

    public List<TelemetryDailyRollup> getDailyRollups(UUID deviceId, Instant from, Instant to) {
        if (dailyRollupRepository == null) return Collections.emptyList();
        return dailyRollupRepository
                .findByDeviceIdAndDayBucketBetweenOrderByDayBucketAsc(deviceId, from, to);
    }

    public List<TelemetryMonthlyRollup> getMonthlyRollups(UUID deviceId, Instant from, Instant to) {
        if (monthlyRollupRepository == null) return Collections.emptyList();
        return monthlyRollupRepository
                .findByDeviceIdAndMonthBucketBetweenOrderByMonthBucketAsc(deviceId, from, to);
    }
}
