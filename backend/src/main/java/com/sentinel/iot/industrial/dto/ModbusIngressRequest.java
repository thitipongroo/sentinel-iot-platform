package com.sentinel.iot.industrial.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

/**
 * Ingress payload from a Modbus TCP edge gateway / master poller.
 */
public record ModbusIngressRequest(
        @NotBlank String deviceId,
        UUID organizationId,
        int slaveId,
        List<ModbusPoint> points,
        Integer exceptionCode,
        Long timestamp
) {}
