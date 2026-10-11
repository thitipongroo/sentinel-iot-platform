package com.sentinel.iot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Pre-computed daily summary rollup for long-term telemetry analytics.
 */
@Entity
@Table(name = "telemetry_daily_rollups")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryDailyRollup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "day_bucket", nullable = false)
    private Instant dayBucket;

    @Column(name = "temp_avg", nullable = false)
    private double tempAvg;

    @Column(name = "temp_min", nullable = false)
    private double tempMin;

    @Column(name = "temp_max", nullable = false)
    private double tempMax;

    @Column(name = "hum_avg", nullable = false)
    private double humAvg;

    @Column(name = "hum_min", nullable = false)
    private double humMin;

    @Column(name = "hum_max", nullable = false)
    private double humMax;

    @Column(name = "smoke_avg")
    private Double smokeAvg;

    @Column(name = "smoke_max")
    private Double smokeMax;

    @Column(name = "motion_count", nullable = false)
    private int motionCount;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
