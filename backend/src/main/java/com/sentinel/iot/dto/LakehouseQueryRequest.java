package com.sentinel.iot.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Parameter payload for executing DuckDB OLAP queries across archived Parquet datasets.
 */
public record LakehouseQueryRequest(
        UUID deviceId,
        UUID organizationId,
        Instant from,
        Instant to,
        String metric,
        String bucket
) {}
