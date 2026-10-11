package com.sentinel.iot.anomaly.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Request payload for dry-run anomaly scoring and adaptive threshold evaluation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryAnomalyEvaluationRequest {
    private UUID deviceId;
    private String deviceName;
    private Double temperature;
    private Double humidity;
    private Double smokePpm;
    private Boolean motion;
    private Map<String, Double> customReadings;
}
