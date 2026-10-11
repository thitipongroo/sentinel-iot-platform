package com.sentinel.iot.anomaly.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents dynamic statistical baseline band (μ ± kσ) for a single sensor.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DynamicBaseline {
    private String sensorKey;
    private double mean;
    private double stdDev;
    private double lowerBand;
    private double upperBand;
    private long sampleCount;
    private double lastObservedValue;
    private double zScore;
    private boolean breached;
    private Instant lastUpdatedAt;

    public static DynamicBaseline initial(String sensorKey, double initialValue) {
        return DynamicBaseline.builder()
                .sensorKey(sensorKey)
                .mean(initialValue)
                .stdDev(1.0)
                .lowerBand(initialValue - 3.0)
                .upperBand(initialValue + 3.0)
                .sampleCount(1)
                .lastObservedValue(initialValue)
                .zScore(0.0)
                .breached(false)
                .lastUpdatedAt(Instant.now())
                .build();
    }
}
