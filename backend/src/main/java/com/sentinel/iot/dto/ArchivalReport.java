package com.sentinel.iot.dto;

import java.util.List;

/**
 * Report summary produced by the automated Parquet archival job.
 */
public record ArchivalReport(
        long recordsArchived,
        long bytesWritten,
        List<String> filesCreated,
        long durationMs,
        long timestamp
) {}
