package com.sentinel.iot;

import com.sentinel.iot.dto.LakehouseQueryRequest;
import com.sentinel.iot.dto.LakehouseQueryResult;
import com.sentinel.iot.service.LakehouseAnalyticsService;
import com.sentinel.iot.service.TelemetryArchivalService;
import com.sentinel.iot.service.TelemetryArchivalService.TelemetryRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("LakehouseAnalyticsServiceTest")
class LakehouseAnalyticsServiceTest {

    private LakehouseAnalyticsService lakehouseService;
    private TelemetryArchivalService archivalService;

    @TempDir
    Path tempDir;

    private UUID orgId;
    private UUID deviceId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        deviceId = UUID.randomUUID();

        lakehouseService = new LakehouseAnalyticsService();
        lakehouseService.setArchivalPath(tempDir.toAbsolutePath().toString());

        archivalService = new TelemetryArchivalService(null);
    }

    @Test
    @DisplayName("queryAggregates: executes vectorized OLAP aggregations across Parquet cold storage")
    void queryAggregates_calculatesSubSecondMetricsFromParquet() throws Exception {
        // Prepare sample Parquet dataset inside Hive partition layout
        File orgDir = new File(tempDir.toFile(), "org_id=" + orgId + "/year=2026/month=08");
        orgDir.mkdirs();
        File parquetFile = new File(orgDir, "telemetry_2026_08.parquet");

        Instant baseTime = Instant.parse("2026-08-10T10:00:00Z");
        List<TelemetryRow> rows = List.of(
                new TelemetryRow(UUID.randomUUID(), deviceId, orgId, 20.0, 50.0, false, 0.0, baseTime),
                new TelemetryRow(UUID.randomUUID(), deviceId, orgId, 30.0, 70.0, true, 2.0, baseTime.plus(1, ChronoUnit.HOURS)),
                new TelemetryRow(UUID.randomUUID(), deviceId, orgId, 40.0, 60.0, false, 1.0, baseTime.plus(2, ChronoUnit.HOURS))
        );
        archivalService.writeParquetFile(parquetFile, rows);

        LakehouseQueryRequest req = new LakehouseQueryRequest(
                deviceId, orgId,
                baseTime.minus(1, ChronoUnit.DAYS),
                baseTime.plus(1, ChronoUnit.DAYS),
                "temperature",
                "day"
        );

        LakehouseQueryResult result = lakehouseService.queryAggregates(req);

        assertThat(result.rowCount()).isGreaterThan(0);
        assertThat(result.columns()).contains("bucket", "sample_count", "temp_avg", "temp_min", "temp_max");

        var firstRow = result.rows().get(0);
        assertThat(((Number) firstRow.get("sample_count")).longValue()).isEqualTo(3L);
        assertThat(((Number) firstRow.get("temp_min")).doubleValue()).isEqualTo(20.0);
        assertThat(((Number) firstRow.get("temp_max")).doubleValue()).isEqualTo(40.0);
        assertThat(((Number) firstRow.get("temp_avg")).doubleValue()).isEqualTo(30.0);
        assertThat(((Number) firstRow.get("hum_avg")).doubleValue()).isEqualTo(60.0);
    }

    @Test
    @DisplayName("queryAggregates: handles missing dataset gracefully without exceptions")
    void queryAggregates_handlesEmptyOrMissingDatasetGracefully() {
        LakehouseQueryRequest req = new LakehouseQueryRequest(
                UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().minus(30, ChronoUnit.DAYS),
                Instant.now(),
                "temperature",
                "day"
        );

        LakehouseQueryResult result = lakehouseService.queryAggregates(req);

        assertThat(result.rowCount()).isEqualTo(0);
        assertThat(result.rows()).isEmpty();
    }

    @Test
    @DisplayName("executeQuery: validates and rejects non-SELECT DDL/DML statements for security")
    void executeQuery_rejectsNonSelectStatements() {
        assertThatThrownBy(() -> lakehouseService.executeQuery("DROP TABLE telemetry"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only read-only SELECT queries are allowed");

        assertThatThrownBy(() -> lakehouseService.executeQuery("DELETE FROM telemetry WHERE 1=1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only read-only SELECT queries are allowed");
    }
}
