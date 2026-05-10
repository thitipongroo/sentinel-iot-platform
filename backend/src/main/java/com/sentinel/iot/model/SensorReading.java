package com.sentinel.iot.model;

/**
 * A single sensor measurement inside a Telemetry record's {@code readings} JSONB column.
 * Stored as a JSON object: {"value": 25.5, "unit": "°C", "quality": "GOOD"}
 *
 * @param value   measured value (may be NaN if quality == BAD and edge sends no data)
 * @param unit    engineering unit string (e.g. "°C", "ppm"); may be null for CUSTOM sensors
 * @param quality data quality flag per OPC-UA convention
 */
public record SensorReading(
        Double value,
        String unit,
        ReadingQuality quality
) {
    /** Convenience factory for the common case of a good-quality reading. */
    public static SensorReading good(double value, String unit) {
        return new SensorReading(value, unit, ReadingQuality.GOOD);
    }

    public static SensorReading uncertain(double value, String unit) {
        return new SensorReading(value, unit, ReadingQuality.UNCERTAIN);
    }

    public static SensorReading bad(String unit) {
        return new SensorReading(null, unit, ReadingQuality.BAD);
    }

    /** True if quality is GOOD and value is non-null. */
    public boolean isUsable() {
        return quality == ReadingQuality.GOOD && value != null;
    }
}
