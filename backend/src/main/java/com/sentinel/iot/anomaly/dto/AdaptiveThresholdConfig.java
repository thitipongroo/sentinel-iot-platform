package com.sentinel.iot.anomaly.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration parameters for the Adaptive Dynamic Threshold Engine.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdaptiveThresholdConfig {

    /**
     * Exponential Moving Average (EMA) alpha learning rate (default: 0.05).
     */
    @Builder.Default
    private double alpha = 0.05;

    /**
     * Sigma multiplier (k) defining the dynamic normal band [μ - kσ, μ + kσ] (default: 3.0).
     */
    @Builder.Default
    private double sigmaMultiplier = 3.0;

    /**
     * Minimum warmup observations before triggering adaptive threshold alarms (default: 10).
     */
    @Builder.Default
    private int minSamples = 10;

    /**
     * Isolation Forest anomaly score threshold for warning alert (default: 0.65).
     */
    @Builder.Default
    private double anomalyScoreThreshold = 0.65;

    /**
     * Critical score threshold for severe anomaly / thermal runaway (default: 0.80).
     */
    @Builder.Default
    private double criticalScoreThreshold = 0.80;

    /**
     * Anomaly baseline update downweight factor to prevent poisoning baselines (default: 0.05).
     */
    @Builder.Default
    private double anomalyDampeningFactor = 0.05;

    /**
     * Master toggle for adaptive thresholding.
     */
    @Builder.Default
    private boolean enabled = true;

    public static AdaptiveThresholdConfig defaults() {
        return AdaptiveThresholdConfig.builder().build();
    }
}
