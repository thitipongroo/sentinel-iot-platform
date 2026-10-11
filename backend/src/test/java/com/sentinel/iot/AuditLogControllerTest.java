package com.sentinel.iot;

import com.sentinel.iot.controller.AuditLogController;
import com.sentinel.iot.model.AuditLog;
import com.sentinel.iot.service.AuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@DisplayName("AuditLogController")
@ExtendWith(MockitoExtension.class)
class AuditLogControllerTest {

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuditLogController controller;

    @Test
    @DisplayName("getAuditLogs delegates parameters to AuditService and returns paginated result")
    void getAuditLogs_delegatesToService() {
        AuditLog log1 = new AuditLog("alice", "DEVICE_CREATE", "/api/v1/devices", "Created sensor", "192.168.1.1");
        Page<AuditLog> page = new PageImpl<>(List.of(log1));

        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();

        when(auditService.getLogs(eq("alice"), eq("DEVICE_CREATE"), eq(from), eq(to), eq(0), eq(20)))
                .thenReturn(page);

        ResponseEntity<Page<AuditLog>> response = controller.getAuditLogs("alice", "DEVICE_CREATE", from, to, 0, 20);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
        assertThat(response.getBody().getContent().get(0).getAction()).isEqualTo("DEVICE_CREATE");
        verify(auditService).getLogs("alice", "DEVICE_CREATE", from, to, 0, 20);
    }
}
