package com.sentinel.iot.model;

import com.sentinel.iot.converter.SensorReadingsConverter;
import com.sentinel.iot.dto.TelemetryMessage;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One telemetry sample persisted to PostgreSQL.
 *
 * <h3>Schema versions</h3>
 * <ul>
 *   <li><b>v1</b> — original fixed fields: temperature, humidity, motion, smokePpm.
 *       Produced by legacy firmware and the MQTT v1 payload format.</li>
 *   <li><b>v2</b> — v1 fields + {@code readings} JSONB (arbitrary sensor map) +
 *       {@code edge} metadata block.  New firmware sends this format.</li>
 * </ul>
 *
 * <p>Both versions are fully readable by this entity.  The {@link #from} factory
 * method handles the v1→v2 synthesis so consumers don't need branching logic.</p>
 */
@Entity
@Table(name = "telemetry", indexes = {
    @Index(name = "idx_telemetry_id",        columnList = "id",              unique = true),
    @Index(name = "idx_telemetry_device_id", columnList = "device_id"),
    @Index(name = "idx_telemetry_timestamp", columnList = "timestamp"),
    @Index(name = "idx_telemetry_device_ts", columnList = "device_id, timestamp")
})
@Data
@NoArgsConstructor
public class Telemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    // ── Schema version ─────────────────────────────────────────────────────────

    /**
     * Payload schema version (default 1).  Consumers must treat unknown versions
     * as v2 to remain forward-compatible with future field additions.
     */
    @Column(name = "schema_version", nullable = false)
    private int schemaVersion = 1;

    // ── v1 fixed fields (backward-compatible, populated from all messages) ─────

    @Column(nullable = false)
    private Double temperature;

    @Column(nullable = false)
    private Double humidity;

    @Column
    private Boolean motion;

    @Column(name = "smoke_ppm")
    private Double smokePpm;

    // ── v2 dynamic sensor readings (JSONB) ─────────────────────────────────────

    /**
     * Arbitrary sensor readings keyed by {@link SensorType#name()} or a custom string.
     * Example: {"TEMPERATURE":{"value":25.5,"unit":"°C","quality":"GOOD"},"CO2_PPM":{...}}
     *
     * <p>For v1 messages this map is synthesized from the fixed fields on ingest so that
     * all downstream consumers (alert engine, analytics) can use a single code path.</p>
     */
    @Convert(converter = SensorReadingsConverter.class)
    @Column(name = "readings", columnDefinition = "jsonb")
    private Map<String, SensorReading> readings;

    // ── v2 edge metadata ───────────────────────────────────────────────────────

    @Embedded
    private EdgeMetadata edge;

    // ── Timestamp ──────────────────────────────────────────────────────────────

    @Column(nullable = false)
    private Instant timestamp;

    // ── Constructors / factories ───────────────────────────────────────────────

    /** Legacy v1 constructor — kept for DLQ consumer and test code. */
    public Telemetry(UUID deviceId, Double temperature, Double humidity,
                     Boolean motion, Double smokePpm) {
        this.deviceId    = deviceId;
        this.temperature = temperature;
        this.humidity    = humidity;
        this.motion      = motion;
        this.smokePpm    = smokePpm;
        this.timestamp   = Instant.now();
        this.readings    = synthesizeReadings(temperature, humidity, motion, smokePpm);
    }

    /**
     * Factory method that builds a {@code Telemetry} from either a v1 or v2 message.
     * For v1 messages the {@code readings} map is synthesized so the alert engine
     * always has a unified view regardless of schema version.
     */
    public static Telemetry from(TelemetryMessage msg, UUID deviceId) {
        Telemetry t = new Telemetry();
        t.deviceId     = deviceId;
        t.schemaVersion = msg.getSchemaVersion();
        t.timestamp    = msg.resolvedTimestamp();

        if (msg.getSchemaVersion() >= 2 && msg.getReadings() != null && !msg.getReadings().isEmpty()) {
            // ── v2 path: readings map is authoritative ───────────────────────
            t.readings = msg.getReadings();

            // Backfill fixed columns from readings for backward-compat queries
            SensorReading temp  = t.readings.get(SensorType.TEMPERATURE.name());
            SensorReading hum   = t.readings.get(SensorType.HUMIDITY.name());
            SensorReading smoke = t.readings.get(SensorType.SMOKE_PPM.name());
            SensorReading mot   = t.readings.get(SensorType.MOTION.name());

            t.temperature = (temp  != null && temp.isUsable())  ? temp.value()            : msg.getTemperature();
            t.humidity    = (hum   != null && hum.isUsable())   ? hum.value()             : msg.getHumidity();
            t.smokePpm    = (smoke != null && smoke.isUsable()) ? smoke.value()            : msg.getSmokePpm();
            t.motion      = (mot   != null && mot.isUsable())   ? mot.value() >= 0.5      : msg.getMotion();

            t.edge        = msg.getEdge();
        } else {
            // ── v1 path: build from fixed fields, synthesize readings ─────────
            t.temperature = msg.getTemperature();
            t.humidity    = msg.getHumidity();
            t.motion      = msg.getMotion();
            t.smokePpm    = msg.getSmokePpm();
            t.readings    = synthesizeReadings(t.temperature, t.humidity, t.motion, t.smokePpm);
        }

        return t;
    }

    /** Builds the canonical readings map from v1 fixed fields. */
    private static Map<String, SensorReading> synthesizeReadings(
            Double temperature, Double humidity, Boolean motion, Double smokePpm) {
        Map<String, SensorReading> r = new HashMap<>();
        if (temperature != null)
            r.put(SensorType.TEMPERATURE.name(), SensorReading.good(temperature, SensorType.TEMPERATURE.defaultUnit));
        if (humidity != null)
            r.put(SensorType.HUMIDITY.name(), SensorReading.good(humidity, SensorType.HUMIDITY.defaultUnit));
        if (smokePpm != null)
            r.put(SensorType.SMOKE_PPM.name(), SensorReading.good(smokePpm, SensorType.SMOKE_PPM.defaultUnit));
        if (motion != null)
            r.put(SensorType.MOTION.name(), SensorReading.good(motion ? 1.0 : 0.0, SensorType.MOTION.defaultUnit));
        return r;
    }
}
