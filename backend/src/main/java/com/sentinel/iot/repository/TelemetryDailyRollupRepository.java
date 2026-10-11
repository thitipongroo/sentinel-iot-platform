package com.sentinel.iot.repository;

import com.sentinel.iot.model.TelemetryDailyRollup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TelemetryDailyRollupRepository extends JpaRepository<TelemetryDailyRollup, UUID> {

    List<TelemetryDailyRollup> findByDeviceIdAndDayBucketBetweenOrderByDayBucketAsc(
            UUID deviceId, Instant from, Instant to);

    List<TelemetryDailyRollup> findByOrganizationIdAndDayBucketBetweenOrderByDayBucketAsc(
            UUID organizationId, Instant from, Instant to);

    @Modifying
    @Query(value = """
            INSERT INTO telemetry_daily_rollups
              (device_id, organization_id, day_bucket,
               temp_avg, temp_min, temp_max,
               hum_avg,  hum_min,  hum_max,
               smoke_avg, smoke_max,
               motion_count, sample_count)
            SELECT
              h.device_id,
              d.organization_id,
              date_trunc('day', h.hour_bucket) AS day_bucket,
              SUM(h.temp_avg * h.sample_count) / NULLIF(SUM(h.sample_count), 0),
              MIN(h.temp_min),
              MAX(h.temp_max),
              SUM(h.hum_avg * h.sample_count) / NULLIF(SUM(h.sample_count), 0),
              MIN(h.hum_min),
              MAX(h.hum_max),
              SUM(COALESCE(h.smoke_avg, 0) * h.sample_count) / NULLIF(SUM(h.sample_count), 0),
              MAX(h.smoke_max),
              SUM(h.motion_count),
              SUM(h.sample_count)
            FROM telemetry_hourly_aggregates h
            LEFT JOIN devices d ON d.id = h.device_id
            WHERE h.hour_bucket >= :from AND h.hour_bucket < :to
            GROUP BY h.device_id, d.organization_id, date_trunc('day', h.hour_bucket)
            ON CONFLICT (device_id, day_bucket) DO UPDATE SET
              temp_avg     = EXCLUDED.temp_avg,
              temp_min     = EXCLUDED.temp_min,
              temp_max     = EXCLUDED.temp_max,
              hum_avg      = EXCLUDED.hum_avg,
              hum_min      = EXCLUDED.hum_min,
              hum_max      = EXCLUDED.hum_max,
              smoke_avg    = EXCLUDED.smoke_avg,
              smoke_max    = EXCLUDED.smoke_max,
              motion_count = EXCLUDED.motion_count,
              sample_count = EXCLUDED.sample_count,
              organization_id = EXCLUDED.organization_id
            """, nativeQuery = true)
    int aggregateDaily(@Param("from") Instant from, @Param("to") Instant to);
}
