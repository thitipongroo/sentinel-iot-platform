package com.sentinel.iot.model;

/**
 * Canonical sensor type vocabulary shared by the Device capability model and
 * Telemetry readings.  Any sensor not covered here uses CUSTOM and carries its
 * key as the readings-map key directly (e.g. "tank_pressure_psi").
 */
public enum SensorType {

    // ── Environmental ──────────────────────────────────────────────────────────
    TEMPERATURE("°C",    -273.15, 2000.0),
    HUMIDITY   ("%RH",       0.0,  100.0),
    PRESSURE   ("hPa",       0.0, 2000.0),
    DEW_POINT  ("°C",    -273.15,  100.0),

    // ── Air quality ────────────────────────────────────────────────────────────
    SMOKE_PPM  ("ppm",       0.0, 10000.0),
    CO2_PPM    ("ppm",       0.0, 50000.0),
    CO_PPM     ("ppm",       0.0,  1000.0),
    VOC_INDEX  ("index",     0.0,   500.0),
    PM25       ("µg/m³",     0.0,   500.0),
    PM10       ("µg/m³",     0.0,   500.0),
    O3_PPB     ("ppb",       0.0,   500.0),

    // ── Motion & presence ──────────────────────────────────────────────────────
    MOTION     ("boolean",   0.0,     1.0),
    VIBRATION_G("g",         0.0,   100.0),
    TILT_DEG   ("°",      -180.0,   180.0),

    // ── Electrical ─────────────────────────────────────────────────────────────
    VOLTAGE_V   ("V",        0.0,  1000.0),
    CURRENT_A   ("A",        0.0,   500.0),
    POWER_W     ("W",        0.0, 100000.0),
    ENERGY_KWH  ("kWh",      0.0, 1.0e9),
    BATTERY_V   ("V",        0.0,    30.0),
    BATTERY_PCT ("%",        0.0,   100.0),

    // ── Connectivity diagnostics ───────────────────────────────────────────────
    SIGNAL_RSSI ("dBm",   -130.0,     0.0),
    SIGNAL_SNR  ("dB",    -20.0,    50.0),

    // ── Optical / acoustic ─────────────────────────────────────────────────────
    LIGHT_LUX  ("lx",        0.0, 150000.0),
    UV_INDEX   ("index",     0.0,    20.0),
    SOUND_DB   ("dB",        0.0,   200.0),

    // ── Fluid / level ──────────────────────────────────────────────────────────
    WATER_LEVEL_PCT("%",     0.0,   100.0),
    FLOW_LPM       ("L/min", 0.0, 10000.0),
    PH             ("pH",    0.0,    14.0),

    // ── Custom / proprietary ───────────────────────────────────────────────────
    CUSTOM         ("",   Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);

    /** SI or common unit for this sensor type. */
    public final String defaultUnit;
    /** Physical lower bound — used for validation, not alert thresholds. */
    public final double physicalMin;
    /** Physical upper bound — used for validation, not alert thresholds. */
    public final double physicalMax;

    SensorType(String defaultUnit, double physicalMin, double physicalMax) {
        this.defaultUnit = defaultUnit;
        this.physicalMin = physicalMin;
        this.physicalMax = physicalMax;
    }

    /** Returns true if {@code value} is within the physical range for this sensor type. */
    public boolean isPhysicallyValid(double value) {
        return value >= physicalMin && value <= physicalMax;
    }
}
