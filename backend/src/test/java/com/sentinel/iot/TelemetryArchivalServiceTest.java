package com.sentinel.iot;

import com.sentinel.iot.dto.ArchivalReport;
import com.sentinel.iot.service.TelemetryArchivalService;
import com.sentinel.iot.service.TelemetryArchivalService.TelemetryRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
@DisplayName("TelemetryArchivalServiceTest")
@ExtendWith(MockitoExtension.class)
class TelemetryArchivalServiceTest {

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private TelemetryArchivalService archivalService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(archivalService, "archivalPath", tempDir.toAbsolutePath().toString());
    }

    @Test
    @DisplayName("writeParquetFile: creates a valid, non-empty Apache Parquet file using DuckDB")
    void writeParquetFile_createsCompressedParquetFile() throws Exception {
        File parquetFile = new File(tempDir.toFile(), "test_telemetry.parquet");
        UUID deviceId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Instant now = Instant.now();

        List<TelemetryRow> rows = List.of(
                new TelemetryRow(UUID.randomUUID(), deviceId, orgId, 25.5, 60.0, false, 5.0, now),
                new TelemetryRow(UUID.randomUUID(), deviceId, orgId, 26.0, 58.5, true, 8.2, now.plusSeconds(60))
        );

        archivalService.writeParquetFile(parquetFile, rows);

        assertThat(parquetFile).exists();
        assertThat(parquetFile.length()).isGreaterThan(0);
        assertThat(parquetFile.getName()).endsWith(".parquet");
    }

    @Test
    @DisplayName("archiveRange: queries aged rows and partitions them into Hive-style Parquet files")
    void archiveRange_groupsByOrgAndMonthAndWritesParquet() {
        UUID deviceId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-15T12:00:00Z");

        Object[] row1 = new Object[]{
                UUID.randomUUID(), deviceId, orgId, 28.5, 62.0, false, 4.0, Timestamp.from(now)
        };
        Object[] row2 = new Object[]{
                UUID.randomUUID(), deviceId, orgId, 29.0, 61.5, true, 4.2, Timestamp.from(now.plusSeconds(300))
        };

        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(row1, row2));

        Instant from = now.minus(30, ChronoUnit.DAYS);
        Instant to = now;

        ArchivalReport report = archivalService.archiveRange(from, to);

        assertThat(report.recordsArchived()).isEqualTo(2);
        assertThat(report.bytesWritten()).isGreaterThan(0);
        assertThat(report.filesCreated()).hasSize(1);
        String createdFile = report.filesCreated().get(0);
        assertThat(createdFile).contains("org_id=" + orgId);
        assertThat(createdFile).contains("year=2026");
        assertThat(createdFile).contains("month=08");
        assertThat(createdFile).endsWith(".parquet");
    }

    @Test
    @DisplayName("archiveRange: returns empty report when database returns no records")
    void archiveRange_returnsEmptyReportWhenNoRecords() {
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        ArchivalReport report = archivalService.archiveRange(Instant.now().minus(60, ChronoUnit.DAYS), Instant.now());

        assertThat(report.recordsArchived()).isEqualTo(0);
        assertThat(report.bytesWritten()).isEqualTo(0);
        assertThat(report.filesCreated()).isEmpty();
    }
}
