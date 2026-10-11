package com.sentinel.iot.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Event notification published when a delta between desired and reported shadow state is detected.
 */
public record DeviceShadowDeltaNotification(
        UUID deviceId,
        UUID organizationId,
        Map<String, Object> state,
        Map<String, Object> metadata,
        Long version,
        Long timestamp,
        String clientToken
) {}
