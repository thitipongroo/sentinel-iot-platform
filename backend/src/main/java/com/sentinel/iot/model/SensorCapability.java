package com.sentinel.iot.model;

/**
 * Declares a sensor that a device is capable of reporting, together with its
 * operational bounds and alert thresholds.  Stored as a value in the Device's
 * {@code capabilities} JSONB column, keyed by {@link SensorType#name()}.
 *
 * <p>Alert evaluation uses {@code warnThreshold} and {@code critThreshold}
 * instead of global application.yml values, enabling per-device tuning.</p>
 *
 * @param unit           engineering unit — defaults to SensorType.defaultUnit if null
 * @param minOperational minimum value the sensor reliably measures
 * @param maxOperational maximum value the sensor reliably measures
 * @param warnThreshold  crossing this value (or dropping below for BATTERY_PCT etc.) raises WARNING
 * @param critThreshold  crossing this value raises CRITICAL
 * @param thresholdDirection ABOVE means alert when value > threshold; BELOW for inverse (e.g. battery)
 * @param enabled        false = sensor is present but data should be ignored (e.g. faulty unit)
 * @param decimalPlaces  display precision hint for dashboard rendering
 */
public record SensorCapability(
        String unit,
        Double minOperational,
        Double maxOperational,
        Double warnThreshold,
        Double critThreshold,
        ThresholdDirection thresholdDirection,
        boolean enabled,
        int decimalPlaces
) {
    public enum ThresholdDirection { ABOVE, BELOW }

    /**
     * Returns true if {@code value} crosses the critical threshold in the configured direction.
     */
    public boolean isCritical(double value) {
        if (critThreshold == null) return false;
        return thresholdDirection == ThresholdDirection.ABOVE
                ? value > critThreshold
                : value < critThreshold;
    }

    /**
     * Returns true if {@code value} crosses the warning threshold but not critical.
     */
    public boolean isWarning(double value) {
        if (warnThreshold == null) return false;
        boolean crossesWarn = thresholdDirection == ThresholdDirection.ABOVE
                ? value > warnThreshold
                : value < warnThreshold;
        return crossesWarn && !isCritical(value);
    }

    /** Convenience builder for the most common sensor shape (alert when value goes above threshold). */
    public static SensorCapability above(String unit, double warn, double crit, int decimals) {
        return new SensorCapability(unit, null, null, warn, crit,
                ThresholdDirection.ABOVE, true, decimals);
    }

    /** Convenience builder for sensors that alert when value drops below threshold (e.g. battery). */
    public static SensorCapability below(String unit, double warn, double crit, int decimals) {
        return new SensorCapability(unit, null, null, warn, crit,
                ThresholdDirection.BELOW, true, decimals);
    }
}
