package com.sentinel.iot.service;

import com.sentinel.iot.dto.LakehouseQueryRequest;
import com.sentinel.iot.dto.LakehouseQueryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * Embedded Lakehouse Analytics Engine powered by DuckDB.
 * Executes sub-second vectorized OLAP aggregations across historical multi-year
 * Apache Parquet datasets stored in cold storage (local disk / S3 / GCS).
 */
@Service
@Slf4j
public class LakehouseAnalyticsService {

    public LakehouseAnalyticsService() {}

    @Value("${telemetry.archival.path:./data/archive}")
    private String archivalPath;

    /**
     * Executes an aggregated time-series metric query across archived Parquet files.
     */
    public LakehouseQueryResult queryAggregates(LakehouseQueryRequest request) {
        long startTime = System.currentTimeMillis();

        String parquetGlob = resolveParquetGlob(request.organizationId());
        File archiveDir = new File(archivalPath);
        if (!archiveDir.exists()) {
            return new LakehouseQueryResult(Collections.emptyList(), Collections.emptyList(), 0, 0, "No archive directory");
        }

        String bucket = (request.bucket() != null && !request.bucket().isBlank())
                ? request.bucket().toLowerCase()
                : "day";
        if (!Set.of("hour", "day", "week", "month", "year").contains(bucket)) {
            bucket = "day";
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ")
           .append("date_trunc('").append(bucket).append("', timestamp) AS bucket, ")
           .append("COUNT(*) AS sample_count, ")
           .append("AVG(temperature) AS temp_avg, ")
           .append("MIN(temperature) AS temp_min, ")
           .append("MAX(temperature) AS temp_max, ")
           .append("AVG(humidity) AS hum_avg, ")
           .append("MIN(humidity) AS hum_min, ")
           .append("MAX(humidity) AS hum_max, ")
           .append("AVG(smoke_ppm) AS smoke_avg, ")
           .append("MAX(smoke_ppm) AS smoke_max ")
           .append("FROM read_parquet('").append(parquetGlob).append("') ")
           .append("WHERE 1=1 ");

        if (request.organizationId() != null) {
            sql.append("AND organization_id = '").append(request.organizationId()).append("' ");
        }
        if (request.deviceId() != null) {
            sql.append("AND device_id = '").append(request.deviceId()).append("' ");
        }
        if (request.from() != null) {
            sql.append("AND timestamp >= '").append(Timestamp.from(request.from())).append("' ");
        }
        if (request.to() != null) {
            sql.append("AND timestamp < '").append(Timestamp.from(request.to())).append("' ");
        }

        sql.append("GROUP BY 1 ORDER BY 1 ASC");

        return executeDuckDbQuery(sql.toString(), startTime, parquetGlob);
    }

    /**
     * Executes arbitrary read-only SQL query across Parquet cold storage using DuckDB.
     */
    public LakehouseQueryResult executeQuery(String sql) {
        long startTime = System.currentTimeMillis();
        String trimmed = sql.trim().toUpperCase();
        if (!trimmed.startsWith("SELECT") && !trimmed.startsWith("DESCRIBE") && !trimmed.startsWith("EXPLAIN")) {
            throw new IllegalArgumentException("Only read-only SELECT queries are allowed in Lakehouse engine");
        }
        return executeDuckDbQuery(sql, startTime, "Custom DuckDB Query");
    }

    private LakehouseQueryResult executeDuckDbQuery(String sql, long startTime, String sourceDescription) {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= colCount; i++) {
                    columns.add(meta.getColumnLabel(i));
                }

                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(columns.get(i - 1), rs.getObject(i));
                    }
                    rows.add(row);
                }

                long duration = System.currentTimeMillis() - startTime;
                log.info("DuckDB query executed in {}ms ({} rows)", duration, rows.size());
                return new LakehouseQueryResult(columns, rows, rows.size(), duration, sourceDescription);
            }
        } catch (SQLException e) {
            // If no matching parquet files exist on disk, return empty result gracefully
            if (e.getMessage() != null && (e.getMessage().contains("No files found that match the pattern") || e.getMessage().contains("Cannot open file"))) {
                long duration = System.currentTimeMillis() - startTime;
                return new LakehouseQueryResult(Collections.emptyList(), Collections.emptyList(), 0, duration, "Parquet dataset empty");
            }
            log.error("DuckDB Lakehouse query failed: {}", e.getMessage());
            throw new RuntimeException("Lakehouse query failed: " + e.getMessage(), e);
        }
    }

    private String resolveParquetGlob(UUID orgId) {
        String base = archivalPath.replace("\\", "/");
        if (orgId != null) {
            return base + "/org_id=" + orgId + "/**/*.parquet";
        }
        return base + "/**/*.parquet";
    }

    public void setArchivalPath(String archivalPath) {
        this.archivalPath = archivalPath;
    }
}
