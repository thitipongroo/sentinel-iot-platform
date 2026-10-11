package com.sentinel.iot.industrial.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

/**
 * Ingress payload from an OPC-UA edge gateway or edge publisher client.
 */
public record OpcUaIngressRequest(
        @NotBlank String deviceId,
        UUID organizationId,
        String endpointUrl,
        List<OpcUaNodeValue> nodes,
        Long timestamp
) {}
