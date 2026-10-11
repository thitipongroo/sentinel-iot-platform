package com.sentinel.iot.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Full Device Twin / Shadow document containing state, metadata, OCC version, and timestamp.
 */
public record DeviceShadowDocument(
        UUID deviceId,
        UUID organizationId,
        DeviceShadowState state,
        Map<String, Object> metadata,
        Long version,
        Long timestamp
) {}
