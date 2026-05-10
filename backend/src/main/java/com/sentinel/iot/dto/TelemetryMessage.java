package com.sentinel.iot.dto;

import com.sentinel.iot.model.EdgeMetadata;
import com.sentinel.iot.model.SensorReading;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * MQTT/Kafka inbound telemetry payload.  Supports two schema versions:
 *
 * <h3>v1 (legacy — all edge firmware up to 2.x)</h3>
 * <pre>
 * {
 *   "deviceId":    "sensor-01",
 *   "temperature": 25.5,
 *   "humidity":    60.0,
 *   "motion":      false,
 *   "smokePpm":    10.5,
 *   "timestamp":   1704067200000
 * }
 * </pre>
 *
 * <h3>v2 (current — firmware 3.0+)</h3>
 * <pre>
 * {
 *   "deviceId":      "sensor-01",
 *   "schemaVersion": 2,
 *   "timestamp":     1704067200000,
 *   "readings": {
 *     "TEMPERATURE": {"value": 25.5,  "unit": "°C",   "quality": "GOOD"},
 *     "HUMIDITY":    {"value": 60.0,  "unit": "%RH",  "quality": "GOOD"},
 *     "CO2_PPM":     {"value": 450.0, "unit": "ppm",  "quality": "GOOD"},
 *     "BATTERY_PCT": {"value": 92.0,  "unit": "%",    "quality": "GOOD"}
 *   },
 *   "edge": {
 *     "firmwareVersion": "3.0.1",
 *     "rssi":            -65,
 *     "batteryPct":      92,
 *     "uptimeSeconds":   86400,
 *     "protocol":        "MQTT"
 *   }
 * }
 * </pre>
 *
 * <p>v2 payloads may still include the v1 top-level fields; the Telemetry factory
 * prefers {@code readings} when {@code schemaVersion >= 2} and falls back to the
 * flat fields for partial payloads.</p>
 */
@Data
public class TelemetryMessage {

    // ── Identity ───────────────────────────────────────────────────────────────

    private String deviceId;

    /**
     * Schema version of this payload.  Absent in v1 messages; Jackson defaults to 1.
     */
    private int schemaVersion = 1;

    // ── v1 fixed fields (backward-compatible) ─────────────────────────────────

    private Double temperature;
    private Double humidity;
    private Boolean motion;
    private Double smokePpm;

    /** Unix epoch milliseconds.  Null means "use server-side Instant.now()". */
    private Long timestamp;

    // ── v2 dynamic readings ────────────────────────────────────────────────────

    /**
     * Sensor readings map.  Key is {@link com.sentinel.iot.model.SensorType#name()}
     * for well-known sensors, or an arbitrary string for custom sensors.
     */
    private Map<String, SensorReading> readings;

    // ── v2 edge metadata ───────────────────────────────────────────────────────

    /** Edge-node diagnostics; null for v1 payloads or firmware that doesn't report them. */
    private EdgeMetadata edge;

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Returns the authoritative timestamp for this message: device-reported if present,
     * server-side now() otherwise.  Using server time for absent timestamps prevents
     * clock-skewed devices from corrupting time-series ordering.
     */
    public Instant resolvedTimestamp() {
        return timestamp != null ? Instant.ofEpochMilli(timestamp) : Instant.now();
    }

    /** True if this message carries a v2 readings map with at least one entry. */
    public boolean hasReadings() {
        return schemaVersion >= 2 && readings != null && !readings.isEmpty();
    }
}
