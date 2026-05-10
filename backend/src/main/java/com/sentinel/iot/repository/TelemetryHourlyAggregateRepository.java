package com.sentinel.iot.repository;

import com.sentinel.iot.model.TelemetryHourlyAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TelemetryHourlyAggregateRepository extends JpaRepository<TelemetryHourlyAggregate, UUID> {

    List<TelemetryHourlyAggregate> findByDeviceIdAndHourBucketBetweenOrderByHourBucketAsc(
            UUID deviceId, Instant from, Instant to);

    /**
     * Upserts hourly aggregates for all devices in the given time window.
     * Uses PostgreSQL's date_trunc + FILTER + ON CONFLICT to be idempotent — safe to re-run.
     */
    @Modifying
    @Query(value = """
            INSERT INTO telemetry_hourly_aggregates
              (device_id, hour_bucket,
               temp_avg, temp_min, temp_max,
               hum_avg,  hum_min,  hum_max,
               smoke_avg, smoke_max,
               motion_count, sample_count)
            SELECT
              device_id,
              date_trunc('hour', timestamp)   AS hour_bucket,
              AVG(temperature),
              MIN(temperature),
              MAX(temperature),
              AVG(humidity),
              MIN(humidity),
              MAX(humidity),
              AVG(smoke_ppm),
              MAX(smoke_ppm),
              COUNT(*) FILTER (WHERE motion = true),
              COUNT(*)
            FROM telemetry
            WHERE timestamp >= :from AND timestamp < :to
            GROUP BY device_id, date_trunc('hour', timestamp)
            ON CONFLICT (device_id, hour_bucket) DO UPDATE SET
              temp_avg     = EXCLUDED.temp_avg,
              temp_min     = EXCLUDED.temp_min,
              temp_max     = EXCLUDED.temp_max,
              hum_avg      = EXCLUDED.hum_avg,
              hum_min      = EXCLUDED.hum_min,
              hum_max      = EXCLUDED.hum_max,
              smoke_avg    = EXCLUDED.smoke_avg,
              smoke_max    = EXCLUDED.smoke_max,
              motion_count = EXCLUDED.motion_count,
              sample_count = EXCLUDED.sample_count
            """, nativeQuery = true)
    int aggregateHourly(@Param("from") Instant from, @Param("to") Instant to);
}
