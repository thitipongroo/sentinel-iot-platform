package com.sentinel.iot.industrial.dto;

import com.sentinel.iot.model.ReadingQuality;

/**
 * Representation of an individual OPC-UA MonitoredItem / DataChange value.
 */
public record OpcUaNodeValue(
        String nodeId,
        String sensorKey,
        Double value,
        String unit,
        String statusCode,
        Long sourceTimestamp
) {
    public ReadingQuality resolveQuality() {
        if (statusCode == null || statusCode.isBlank()) {
            return (value != null) ? ReadingQuality.GOOD : ReadingQuality.BAD;
        }
        String sc = statusCode.toUpperCase();
        if (sc.contains("GOOD") || "0X00000000".equals(sc) || "0".equals(sc)) {
            return ReadingQuality.GOOD;
        }
        if (sc.contains("UNCERTAIN")) {
            return ReadingQuality.UNCERTAIN;
        }
        return ReadingQuality.BAD;
    }
}
