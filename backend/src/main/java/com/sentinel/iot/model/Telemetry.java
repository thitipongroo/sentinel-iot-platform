package com.sentinel.iot.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "telemetry", indexes = {
    @Index(name = "idx_telemetry_id",         columnList = "id",              unique = true),
    @Index(name = "idx_telemetry_device_id",  columnList = "device_id"),
    @Index(name = "idx_telemetry_timestamp",  columnList = "timestamp"),
    @Index(name = "idx_telemetry_device_ts",  columnList = "device_id, timestamp")
})
@Data
@NoArgsConstructor
public class Telemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(nullable = false)
    private Double temperature;

    @Column(nullable = false)
    private Double humidity;

    @Column
    private Boolean motion;

    @Column(name = "smoke_ppm")
    private Double smokePpm;

    @Column(nullable = false)
    private Instant timestamp;

    public Telemetry(UUID deviceId, Double temperature, Double humidity, Boolean motion, Double smokePpm) {
        this.deviceId = deviceId;
        this.temperature = temperature;
        this.humidity = humidity;
        this.motion = motion;
        this.smokePpm = smokePpm;
        this.timestamp = Instant.now();
    }
}
