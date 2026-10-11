package com.sentinel.iot.repository;

import com.sentinel.iot.model.TelemetryMonthlyRollup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TelemetryMonthlyRollupRepository extends JpaRepository<TelemetryMonthlyRollup, UUID> {

    List<TelemetryMonthlyRollup> findByDeviceIdAndMonthBucketBetweenOrderByMonthBucketAsc(
            UUID deviceId, Instant from, Instant to);

    List<TelemetryMonthlyRollup> findByOrganizationIdAndMonthBucketBetweenOrderByMonthBucketAsc(
            UUID organizationId, Instant from, Instant to);

    @Modifying
    @Query(value = """
            INSERT INTO telemetry_monthly_rollups
              (device_id, organization_id, month_bucket,
               temp_avg, temp_min, temp_max,
               hum_avg,  hum_min,  hum_max,
               smoke_avg, smoke_max,
               motion_count, sample_count)
            SELECT
              device_id,
              organization_id,
              date_trunc('month', day_bucket) AS month_bucket,
              SUM(temp_avg * sample_count) / NULLIF(SUM(sample_count), 0),
              MIN(temp_min),
              MAX(temp_max),
              SUM(hum_avg * sample_count) / NULLIF(SUM(sample_count), 0),
              MIN(hum_min),
              MAX(hum_max),
              SUM(COALESCE(smoke_avg, 0) * sample_count) / NULLIF(SUM(sample_count), 0),
              MAX(smoke_max),
              SUM(motion_count),
              SUM(sample_count)
            FROM telemetry_daily_rollups
            WHERE day_bucket >= :from AND day_bucket < :to
            GROUP BY device_id, organization_id, date_trunc('month', day_bucket)
            ON CONFLICT (device_id, month_bucket) DO UPDATE SET
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
    int aggregateMonthly(@Param("from") Instant from, @Param("to") Instant to);
}
