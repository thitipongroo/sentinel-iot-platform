package com.sentinel.iot;

import com.sentinel.iot.controller.DeviceShadowController;
import com.sentinel.iot.dto.DeviceShadowDocument;
import com.sentinel.iot.dto.DeviceShadowState;
import com.sentinel.iot.dto.DeviceShadowUpdateRequest;
import com.sentinel.iot.security.TenantContext;
import com.sentinel.iot.service.AuditService;
import com.sentinel.iot.service.DeviceShadowService;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("DeviceShadowController")
@ExtendWith(MockitoExtension.class)
class DeviceShadowControllerTest {

    @Mock
    private DeviceShadowService shadowService;

    @Mock
    private AuditService auditService;

    @Mock
    private Authentication authentication;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private DeviceShadowController controller;

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
    @DisplayName("getShadow: returns shadow document with 200 OK")
    void getShadow_returnsOk() {
        DeviceShadowState state = new DeviceShadowState(
                Map.of("interval", 15),
                Map.of("interval", 60),
                Map.of("interval", 15)
        );
        DeviceShadowDocument doc = new DeviceShadowDocument(
                deviceId, testOrg, state, Collections.emptyMap(), 5L, Instant.now().getEpochSecond()
        );
        when(shadowService.getShadow(deviceId, testOrg)).thenReturn(doc);

        ResponseEntity<DeviceShadowDocument> response = controller.getShadow(deviceId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().version()).isEqualTo(5L);
        assertThat(response.getBody().state().delta()).containsEntry("interval", 15);
        verify(shadowService).getShadow(deviceId, testOrg);
    }

    @Test
    @DisplayName("updateShadow: updates state, logs audit entry, and returns 200 OK")
    void updateShadow_updatesAndAudits() {
        when(authentication.getName()).thenReturn("engineer-bob");
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.1");

        DeviceShadowUpdateRequest req = new DeviceShadowUpdateRequest(
                Map.of("telemetryIntervalSec", 10), null, 5L, "client-tok-1"
        );
        DeviceShadowState state = new DeviceShadowState(
                Map.of("telemetryIntervalSec", 10),
                Map.of("telemetryIntervalSec", 60),
                Map.of("telemetryIntervalSec", 10)
        );
        DeviceShadowDocument doc = new DeviceShadowDocument(
                deviceId, testOrg, state, Collections.emptyMap(), 6L, Instant.now().getEpochSecond()
        );
        when(shadowService.updateShadow(deviceId, testOrg, req)).thenReturn(doc);

        ResponseEntity<DeviceShadowDocument> response = controller.updateShadow(deviceId, req, authentication, httpRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().version()).isEqualTo(6L);
        verify(shadowService).updateShadow(deviceId, testOrg, req);
        verify(auditService).log(eq("engineer-bob"), eq("DEVICE_SHADOW_UPDATE"),
                eq("/api/v1/devices/" + deviceId + "/shadow"), contains("version=6"), anyString());
    }

    @Test
    @DisplayName("deleteShadow: removes shadow, logs audit entry, and returns 204 No Content")
    void deleteShadow_removesAndAudits() {
        when(authentication.getName()).thenReturn("admin-alice");
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.2");

        ResponseEntity<Void> response = controller.deleteShadow(deviceId, authentication, httpRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(shadowService).deleteShadow(deviceId, testOrg);
        verify(auditService).log(eq("admin-alice"), eq("DEVICE_SHADOW_DELETE"),
                eq("/api/v1/devices/" + deviceId + "/shadow"), contains(deviceId.toString()), anyString());
    }
}
