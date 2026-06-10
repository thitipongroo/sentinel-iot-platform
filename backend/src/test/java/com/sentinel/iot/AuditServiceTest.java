package com.sentinel.iot;

import com.sentinel.iot.model.AuditLog;
import com.sentinel.iot.repository.AuditLogRepository;
import com.sentinel.iot.security.TenantContext;
import com.sentinel.iot.service.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("AuditService")
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock AuditLogRepository auditLogRepository;
    @InjectMocks AuditService auditService;

    private final UUID orgId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @SuppressWarnings("null")
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(auditService, "retentionDays", 90);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ── log() ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("log()")
    class Log {

        @SuppressWarnings("null")
        @Test
        @DisplayName("saves audit entry with all fields populated from arguments and tenant context")
        void log_savesAuditEntryWithCorrectFields() {
            TenantContext.set(orgId);

            auditService.log("alice", "CREATE", "Device", "sensor-1 created", "10.0.0.1");

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());
            AuditLog entry = captor.getValue();

            assertThat(entry.getUsername()).as("username").isEqualTo("alice");
            assertThat(entry.getAction()).as("action").isEqualTo("CREATE");
            assertThat(entry.getResource()).as("resource").isEqualTo("Device");
            assertThat(entry.getDetail()).as("detail").isEqualTo("sensor-1 created");
            assertThat(entry.getIpAddress()).as("ip").isEqualTo("10.0.0.1");
            assertThat(entry.getOrganizationId()).as("orgId").isEqualTo(orgId);
            assertThat(entry.getTimestamp())
                    .as("timestamp within 5 seconds of now")
                    .isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("reads organization ID from TenantContext (not from arguments)")
        void log_setsOrganizationId_fromTenantContext() {
            TenantContext.set(orgId);

            auditService.log("bob", "DELETE", "Device", "sensor-2 deleted", "192.168.1.1");

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());
            assertThat(captor.getValue().getOrganizationId())
                    .as("orgId must come from TenantContext")
                    .isEqualTo(orgId);
        }

        @Test
        @DisplayName("swallows repository exceptions so audit never crashes the caller (fail-open)")
        void log_swallowsException_failOpen() {
            TenantContext.set(orgId);
            doThrow(new RuntimeException("DB unavailable")).when(auditLogRepository).save(any());

            assertThatNoException().isThrownBy(
                    () -> auditService.log("alice", "CREATE", "Device", "detail", "10.0.0.1"));
        }
    }

    // ── purgeOldAuditLogs() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("purgeOldAuditLogs()")
    class PurgeOldAuditLogs {

        @SuppressWarnings("null")
        @Test
        @DisplayName("deletes records older than the configured retention window")
        void purgeOldAuditLogs_callsDeleteOlderThan_withRetentionDayCutoff() {
            when(auditLogRepository.deleteOlderThan(any())).thenReturn(12);
            Instant before = Instant.now().minus(90, ChronoUnit.DAYS);

            auditService.purgeOldAuditLogs();

            ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(auditLogRepository).deleteOlderThan(cutoffCaptor.capture());
            Instant cutoff = cutoffCaptor.getValue();
            Instant after = Instant.now().minus(90, ChronoUnit.DAYS);
            assertThat(cutoff)
                    .as("cutoff must be within 5 seconds of 90 days ago")
                    .isBetween(before.minusSeconds(5), after.plusSeconds(5));
        }
    }
}
