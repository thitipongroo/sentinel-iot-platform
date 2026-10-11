package com.sentinel.iot.dto;

import java.util.Map;

/**
 * Encapsulates the three-section state model: desired, reported, and computed delta.
 */
public record DeviceShadowState(
        Map<String, Object> desired,
        Map<String, Object> reported,
        Map<String, Object> delta
) {}
