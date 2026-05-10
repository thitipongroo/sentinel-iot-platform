package com.sentinel.iot.dto;

import com.sentinel.iot.model.SensorCapability;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Request body for PUT /api/devices/{id}/capabilities.
 * Replaces the device's entire capability map; send the full desired state.
 *
 * Example:
 * <pre>
 * {
 *   "TEMPERATURE": {
 *     "unit": "°C", "warnThreshold": 75.0, "critThreshold": 90.0,
 *     "thresholdDirection": "ABOVE", "enabled": true, "decimalPlaces": 1
 *   },
 *   "BATTERY_PCT": {
 *     "unit": "%", "warnThreshold": 20.0, "critThreshold": 10.0,
 *     "thresholdDirection": "BELOW", "enabled": true, "decimalPlaces": 0
 *   }
 * }
 * </pre>
 */
public record DeviceCapabilityRequest(
        @NotNull Map<String, SensorCapability> capabilities
) {}
