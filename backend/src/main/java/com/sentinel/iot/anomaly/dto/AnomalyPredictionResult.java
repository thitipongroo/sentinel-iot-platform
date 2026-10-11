package com.sentinel.iot.anomaly.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Result of realtime multivariate ML anomaly inference and adaptive threshold evaluation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyPredictionResult {

    private UUID deviceId;
    private UUID organizationId;
    private String deviceName;

    /**
     * True if either multivariate anomaly score exceeds threshold or dynamic threshold is breached.
     */
    private boolean anomaly;

    /**
     * Normalized anomaly score between 0.0 (strictly nominal) and 1.0 (extreme anomaly).
     */
    private double anomalyScore;

    /**
     * Categorized anomaly classification:
     * - NONE
     * - THERMAL_RUNAWAY
     * - MULTIVARIATE_CORRELATION_BREAKDOWN
     * - DYNAMIC_THRESHOLD_BREACH
     * - RATE_OF_CHANGE_SPIKE
     */
    private String anomalyType;

    /**
     * Statistical confidence level (e.g. 0.98 = 98%).
     */
    private double confidence;

    /**
     * Alert severity: INFO, WARNING, CRITICAL.
     */
    private String severity;

    /**
     * Percentage contributions of individual sensor dimensions to the anomaly score.
     */
    private Map<String, Double> featureContributions;

    /**
     * State of adaptive threshold baselines for all evaluated sensors.
     */
    private Map<String, DynamicBaseline> sensorBaselines;

    /**
     * Human-readable diagnostic summary explaining the anomaly reason and physical symptoms.
     */
    private String summary;

    @Builder.Default
    private Instant evaluatedAt = Instant.now();

    public String anomalyType() {
        return anomalyType;
    }

    public double anomalyScore() {
        return anomalyScore;
    }

    public static AnomalyPredictionResult nominal(UUID deviceId, UUID organizationId, String deviceName,
                                                  Map<String, DynamicBaseline> baselines) {
        return AnomalyPredictionResult.builder()
                .deviceId(deviceId)
                .organizationId(organizationId)
                .deviceName(deviceName)
                .anomaly(false)
                .anomalyScore(0.15)
                .anomalyType("NONE")
                .confidence(0.99)
                .severity("INFO")
                .sensorBaselines(baselines)
                .summary("All sensor readings within nominal operating bands")
                .evaluatedAt(Instant.now())
                .build();
    }
}
