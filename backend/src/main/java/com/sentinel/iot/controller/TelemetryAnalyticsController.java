package com.sentinel.iot.controller;

import com.sentinel.iot.dto.ArchivalReport;
import com.sentinel.iot.dto.LakehouseQueryRequest;
import com.sentinel.iot.dto.LakehouseQueryResult;
import com.sentinel.iot.model.TelemetryDailyRollup;
import com.sentinel.iot.model.TelemetryMonthlyRollup;
import com.sentinel.iot.security.TenantContext;
import com.sentinel.iot.service.AuditService;
import com.sentinel.iot.service.LakehouseAnalyticsService;
import com.sentinel.iot.service.TelemetryArchivalService;
import com.sentinel.iot.service.TelemetryRetentionService;
import com.sentinel.iot.util.HttpUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Controller for Lakehouse OLAP analytics, multi-year tiered rollups, and cold storage Parquet archival.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics & Lakehouse", description = "Multi-year historical analytics, tiered daily/monthly rollups, and cold Parquet queries")
public class TelemetryAnalyticsController {

    private final TelemetryRetentionService retentionService;
    private final LakehouseAnalyticsService lakehouseService;
    private final TelemetryArchivalService archivalService;
    private final AuditService auditService;

    @GetMapping("/rollups/daily")
    @Operation(summary = "Get daily pre-computed rollups for a device",
               description = "Returns day-bucketed averages, min, max, and sample counts for 1-12 month trends in < 5ms.")
    public ResponseEntity<List<TelemetryDailyRollup>> getDailyRollups(
            @RequestParam UUID deviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(retentionService.getDailyRollups(deviceId, from, to));
    }

    @GetMapping("/rollups/monthly")
    @Operation(summary = "Get monthly pre-computed rollups for a device",
               description = "Returns month-bucketed rollups across multi-year historical horizons in < 5ms.")
    public ResponseEntity<List<TelemetryMonthlyRollup>> getMonthlyRollups(
            @RequestParam UUID deviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(retentionService.getMonthlyRollups(deviceId, from, to));
    }

    @PostMapping("/lakehouse/query")
    @Operation(summary = "Execute high-speed DuckDB OLAP query across Parquet cold storage",
               description = "Queries multi-year columnar Parquet files directly with sub-second response time and zero database overhead.")
    public ResponseEntity<LakehouseQueryResult> queryLakehouse(@RequestBody LakehouseQueryRequest request) {
        UUID orgId = request.organizationId() != null ? request.organizationId() : TenantContext.get();
        LakehouseQueryRequest enriched = new LakehouseQueryRequest(
                request.deviceId(),
                orgId,
                request.from(),
                request.to(),
                request.metric(),
                request.bucket()
        );
        return ResponseEntity.ok(lakehouseService.queryAggregates(enriched));
    }

    @PostMapping("/archive/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Trigger on-demand Parquet archival for aged telemetry (ADMIN only)",
               description = "Streams telemetry rows into Hive-partitioned Apache Parquet files on cold storage.")
    public ResponseEntity<ArchivalReport> triggerArchival(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        Instant actualTo = (to != null) ? to : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant actualFrom = (from != null) ? from : actualTo.minus(30, ChronoUnit.DAYS);

        ArchivalReport report = archivalService.archiveRange(actualFrom, actualTo);

        if (authentication != null) {
            String clientIp = HttpUtils.resolveClientIp(httpRequest);
            auditService.log(authentication.getName(), "TELEMETRY_COLD_ARCHIVE",
                    "/api/v1/analytics/archive/trigger",
                    "records=" + report.recordsArchived() + ",bytes=" + report.bytesWritten(), clientIp);
        }

        return ResponseEntity.ok(report);
    }
}
