package com.sentinel.iot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Diagnostics reported by the edge node alongside telemetry data.
 * Embedded directly into the {@link Telemetry} row — no join required.
 *
 * <p>All fields are nullable; edge firmware does not need to supply every field.
 * Absent fields indicate the edge device does not track that metric.</p>
 */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdgeMetadata {

    /** Firmware version string of the edge node (semver preferred, e.g. "2.3.1"). */
    @Column(name = "edge_firmware_version", length = 50)
    private String firmwareVersion;

    /** IP address of the edge node at the time of transmission. */
    @Column(name = "edge_ip", length = 50)
    private String ipAddress;

    /** Edge node uptime in seconds since last reboot. */
    @Column(name = "edge_uptime_seconds")
    private Long uptimeSeconds;

    /** WiFi/cellular RSSI in dBm (typically -30 to -110). */
    @Column(name = "edge_rssi")
    private Integer rssi;

    /** Signal-to-noise ratio in dB. */
    @Column(name = "edge_snr")
    private Integer snr;

    /** Battery voltage in Volts; null for mains-powered devices. */
    @Column(name = "edge_battery_voltage")
    private Double batteryVoltage;

    /** Battery state-of-charge as a percentage (0–100). */
    @Column(name = "edge_battery_pct")
    private Integer batteryPct;

    /** Free heap memory on the edge MCU in bytes; useful for OOM diagnosis. */
    @Column(name = "edge_free_heap_bytes")
    private Integer freeHeapBytes;

    /**
     * Transport protocol used for this message (e.g. "MQTT", "CoAP", "HTTP").
     * Helps diagnose protocol-level issues during incident analysis.
     */
    @Column(name = "edge_protocol", length = 20)
    private String protocol;
}
