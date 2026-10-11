package com.sentinel.iot.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.Map;

/**
 * Request payload for updating device shadow desired or reported states.
 */
public record DeviceShadowUpdateRequest(
        Map<String, Object> desired,
        Map<String, Object> reported,

        @JsonAlias({"expectedVersion", "expected_version"})
        Long version,

        @JsonAlias({"client_token"})
        String clientToken
) {}
