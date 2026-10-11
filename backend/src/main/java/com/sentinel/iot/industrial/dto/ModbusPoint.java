package com.sentinel.iot.industrial.dto;

import com.sentinel.iot.model.ReadingQuality;

/**
 * Representation of a single Modbus data point (register or coil).
 */
public record ModbusPoint(
        String sensorKey,
        Number rawValue,
        String unit,
        Double scaleFactor,
        Double offset,
        ReadingQuality quality
) {
    public double getScaledValue() {
        double raw = (rawValue != null) ? rawValue.doubleValue() : 0.0;
        double scale = (scaleFactor != null) ? scaleFactor : 1.0;
        double off = (offset != null) ? offset : 0.0;
        return (raw * scale) + off;
    }
}
