package com.sentinel.iot.model;

/**
 * OPC-UA-inspired data quality flag carried per sensor reading.
 * Consumers should treat UNCERTAIN as advisory and BAD as unusable.
 */
public enum ReadingQuality {
    /** Sensor operating within spec; value is trustworthy. */
    GOOD,
    /** Sensor may be warming up, out-of-range, or in degraded mode; use with caution. */
    UNCERTAIN,
    /** Sensor fault, wire break, or communications error; value should be discarded. */
    BAD
}
