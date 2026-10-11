package com.sentinel.iot;

import com.sentinel.iot.controller.TelemetryAnalyticsController;
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
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("TelemetryAnalyticsControllerTest")
@ExtendWith(MockitoExtension.class)
class TelemetryAnalyticsControllerTest {

    @Mock
    private TelemetryRetentionService retentionService;

    @Mock
    private LakehouseAnalyticsService lakehouseService;

    @Mock
    private TelemetryArchivalService archivalService;

    @Mock
    private AuditService auditService;

    @Mock
    private Authentication authentication;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private TelemetryAnalyticsController controller;

    private final UUID testOrg = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final UUID deviceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(testOrg);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getDailyRollups: delegates to service and returns 200 OK")
    void getDailyRollups_returnsRecords() {
        Instant from = Instant.now().minusSeconds(86400 * 7);
        Instant to = Instant.now();
        TelemetryDailyRollup rollup = TelemetryDailyRollup.builder()
                .deviceId(deviceId)
                .dayBucket(from)
                .tempAvg(22.5)
                .sampleCount(100)
                .build();
        when(retentionService.getDailyRollups(deviceId, from, to)).thenReturn(List.of(rollup));

        ResponseEntity<List<TelemetryDailyRollup>> res = controller.getDailyRollups(deviceId, from, to);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).hasSize(1);
        verify(retentionService).getDailyRollups(deviceId, from, to);
    }

    @Test
    @DisplayName("getMonthlyRollups: delegates to service and returns 200 OK")
    void getMonthlyRollups_returnsRecords() {
        Instant from = Instant.now().minusSeconds(86400 * 60);
        Instant to = Instant.now();
        TelemetryMonthlyRollup rollup = TelemetryMonthlyRollup.builder()
                .deviceId(deviceId)
                .monthBucket(from)
                .tempAvg(24.0)
                .sampleCount(5000)
                .build();
        when(retentionService.getMonthlyRollups(deviceId, from, to)).thenReturn(List.of(rollup));

        ResponseEntity<List<TelemetryMonthlyRollup>> res = controller.getMonthlyRollups(deviceId, from, to);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).hasSize(1);
        verify(retentionService).getMonthlyRollups(deviceId, from, to);
    }

    @Test
    @DisplayName("queryLakehouse: enriches tenant from context and executes DuckDB query")
    void queryLakehouse_enrichesOrgAndQueries() {
        LakehouseQueryRequest req = new LakehouseQueryRequest(
                deviceId, null, Instant.now().minusSeconds(3600), Instant.now(), "temp", "hour"
        );
        LakehouseQueryResult result = new LakehouseQueryResult(
                List.of("bucket", "temp_avg"), Collections.emptyList(), 0, 15L, "DuckDB"
        );
        when(lakehouseService.queryAggregates(argThat(r -> testOrg.equals(r.organizationId()))))
                .thenReturn(result);

        ResponseEntity<LakehouseQueryResult> res = controller.queryLakehouse(req);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().executionTimeMs()).isEqualTo(15L);
        verify(lakehouseService).queryAggregates(any());
    }

    @Test
    @DisplayName("triggerArchival: calls archivalService and logs audit event")
    void triggerArchival_executesAndAudits() {
        when(authentication.getName()).thenReturn("admin-dave");
        when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.55");

        Instant from = Instant.now().minusSeconds(86400 * 60);
        Instant to = Instant.now().minusSeconds(86400 * 30);
        ArchivalReport report = new ArchivalReport(1500, 102400, List.of("telemetry.parquet"), 250L, Instant.now().toEpochMilli());
        when(archivalService.archiveRange(from, to)).thenReturn(report);

        ResponseEntity<ArchivalReport> res = controller.triggerArchival(from, to, authentication, httpRequest);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().recordsArchived()).isEqualTo(1500L);
        verify(archivalService).archiveRange(from, to);
        verify(auditService).log(eq("admin-dave"), eq("TELEMETRY_COLD_ARCHIVE"),
                eq("/api/v1/analytics/archive/trigger"), contains("records=1500"), anyString());
    }
}
