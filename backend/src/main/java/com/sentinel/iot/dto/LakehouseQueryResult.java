package com.sentinel.iot.dto;

import java.util.List;
import java.util.Map;

/**
 * Result returned by the DuckDB Lakehouse analytics engine.
 */
public record LakehouseQueryResult(
        List<String> columns,
        List<Map<String, Object>> rows,
        long rowCount,
        long executionTimeMs,
        String source
) {}
