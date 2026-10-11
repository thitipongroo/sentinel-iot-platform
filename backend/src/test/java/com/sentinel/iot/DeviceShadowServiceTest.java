package com.sentinel.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.dto.DeviceShadowDocument;
import com.sentinel.iot.dto.DeviceShadowUpdateRequest;
import com.sentinel.iot.exception.DeviceShadowConflictException;
import com.sentinel.iot.model.Device;
import com.sentinel.iot.model.DeviceShadow;
import com.sentinel.iot.repository.DeviceRepository;
import com.sentinel.iot.repository.DeviceShadowRepository;
import com.sentinel.iot.service.DeviceShadowService;
import com.sentinel.iot.service.MqttPublisherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("DeviceShadowService")
@ExtendWith(MockitoExtension.class)
class DeviceShadowServiceTest {

    @Mock
    private DeviceShadowRepository shadowRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private MqttPublisherService mqttPublisher;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private DeviceShadowService shadowService;

    private UUID deviceId;
    private UUID orgId;
    private Device device;
    private DeviceShadow existingShadow;

    @BeforeEach
    void setUp() {
        deviceId = UUID.randomUUID();
        orgId = UUID.randomUUID();

        device = new Device();
        device.setId(deviceId);
        device.setName("edge-sensor-42");
        device.setOrganizationId(orgId);

        Map<String, Object> desired = new HashMap<>();
        desired.put("telemetryIntervalSec", 15);
        desired.put("tempThresholdAlert", 75.0);

        Map<String, Object> reported = new HashMap<>();
        reported.put("telemetryIntervalSec", 60);
        reported.put("tempThresholdAlert", 75.0);
        reported.put("batteryLevel", 98.5);

        Map<String, Object> meta = new HashMap<>();
        meta.put("desired", new HashMap<String, Object>());
        meta.put("reported", new HashMap<String, Object>());

        existingShadow = DeviceShadow.builder()
                .deviceId(deviceId)
                .organizationId(orgId)
                .version(10L)
                .desiredState(desired)
                .reportedState(reported)
                .metadata(meta)
                .updatedAt(Instant.now())
                .build();
    }

    // ── Delta computation engine tests ──────────────────────────────────────

    @Test
    @DisplayName("computeDelta: returns empty map when desired is null or empty")
    void computeDelta_returnsEmptyWhenDesiredIsEmpty() {
        Map<String, Object> delta = shadowService.computeDelta(null, Map.of("temp", 25.0));
        assertThat(delta).isEmpty();

        delta = shadowService.computeDelta(Collections.emptyMap(), Map.of("temp", 25.0));
        assertThat(delta).isEmpty();
    }

    @Test
    @DisplayName("computeDelta: includes desired properties when reported is missing or null")
    void computeDelta_returnsDesiredPropsWhenReportedMissing() {
        Map<String, Object> desired = Map.of("valve", "OPEN", "speed", 100);
        Map<String, Object> delta = shadowService.computeDelta(desired, null);

        assertThat(delta).containsEntry("valve", "OPEN");
        assertThat(delta).containsEntry("speed", 100);
    }

    @Test
    @DisplayName("computeDelta: returns only differing properties between desired and reported")
    void computeDelta_returnsDifferingPropertiesOnly() {
        Map<String, Object> desired = Map.of(
                "telemetryIntervalSec", 15,
                "tempThresholdAlert", 75.0,
                "valveStatus", "CLOSED"
        );
        Map<String, Object> reported = Map.of(
                "telemetryIntervalSec", 60,
                "tempThresholdAlert", 75.0,
                "valveStatus", "CLOSED",
                "batteryLevel", 95.0
        );

        Map<String, Object> delta = shadowService.computeDelta(desired, reported);

        assertThat(delta).hasSize(1);
        assertThat(delta).containsEntry("telemetryIntervalSec", 15);
        assertThat(delta).doesNotContainKey("tempThresholdAlert");
        assertThat(delta).doesNotContainKey("valveStatus");
        assertThat(delta).doesNotContainKey("batteryLevel");
    }

    @Test
    @DisplayName("computeDelta: state convergence results in empty delta map")
    void computeDelta_returnsEmptyWhenReportedMatchesDesired_convergence() {
        Map<String, Object> desired = Map.of("fanSpeed", 3, "mode", "AUTO");
        Map<String, Object> reported = Map.of("fanSpeed", 3, "mode", "AUTO", "uptimeSec", 12345);

        Map<String, Object> delta = shadowService.computeDelta(desired, reported);

        assertThat(delta).isEmpty();
    }

    @Test
    @DisplayName("computeDelta: recursively diffs nested configuration objects")
    void computeDelta_handlesNestedObjectDiffCorrectly() {
        Map<String, Object> desired = Map.of(
                "network", Map.of("ip", "192.168.1.100", "dns", "8.8.8.8"),
                "status", "ACTIVE"
        );
        Map<String, Object> reported = Map.of(
                "network", Map.of("ip", "192.168.1.100", "dns", "1.1.1.1"),
                "status", "ACTIVE"
        );

        Map<String, Object> delta = shadowService.computeDelta(desired, reported);

        assertThat(delta).hasSize(1);
        assertThat(delta).containsKey("network");
        @SuppressWarnings("unchecked")
        Map<String, Object> networkDelta = (Map<String, Object>) delta.get("network");
        assertThat(networkDelta).containsEntry("dns", "8.8.8.8");
        assertThat(networkDelta).doesNotContainKey("ip");
    }

    // ── Read shadow tests (getShadow) ───────────────────────────────────────

    @Test
    @DisplayName("getShadow: returns sub-millisecond cached document from Redis on hit")
    void getShadow_returnsFromRedisCacheWhenPresent() throws Exception {
        DeviceShadowDocument cachedDoc = new DeviceShadowDocument(
                deviceId, orgId, null, Collections.emptyMap(), 10L, Instant.now().getEpochSecond()
        );
        String cachedJson = objectMapper.writeValueAsString(cachedDoc);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("device:shadow:" + orgId + ":" + deviceId)).thenReturn(cachedJson);

        DeviceShadowDocument result = shadowService.getShadow(deviceId, orgId);

        assertThat(result.deviceId()).isEqualTo(deviceId);
        assertThat(result.version()).isEqualTo(10L);
        verifyNoInteractions(deviceRepository);
        verifyNoInteractions(shadowRepository);
    }

    @Test
    @DisplayName("getShadow: falls back to PostgreSQL, computes delta, and caches in Redis on cache miss")
    void getShadow_fetchesFromDbAndCachesInRedisWhenCacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("device:shadow:" + orgId + ":" + deviceId)).thenReturn(null);
        when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.of(device));
        when(shadowRepository.findByDeviceIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.of(existingShadow));

        DeviceShadowDocument result = shadowService.getShadow(deviceId, orgId);

        assertThat(result.deviceId()).isEqualTo(deviceId);
        assertThat(result.version()).isEqualTo(10L);
        assertThat(result.state().desired()).containsEntry("telemetryIntervalSec", 15);
        assertThat(result.state().reported()).containsEntry("telemetryIntervalSec", 60);
        assertThat(result.state().delta()).containsEntry("telemetryIntervalSec", 15);

        verify(valueOperations).set(eq("device:shadow:" + orgId + ":" + deviceId), anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("getShadow: initializes empty shadow document when device exists but has no shadow yet")
    void getShadow_createsInitialShadowWhenNotPresentInDb() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("device:shadow:" + orgId + ":" + deviceId)).thenReturn(null);
        when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.of(device));
        when(shadowRepository.findByDeviceIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.empty());
        when(shadowRepository.save(any(DeviceShadow.class))).thenAnswer(inv -> inv.getArgument(0));

        DeviceShadowDocument result = shadowService.getShadow(deviceId, orgId);

        assertThat(result.deviceId()).isEqualTo(deviceId);
        assertThat(result.version()).isEqualTo(1L);
        assertThat(result.state().desired()).isEmpty();
        assertThat(result.state().reported()).isEmpty();
        assertThat(result.state().delta()).isEmpty();
        verify(shadowRepository).save(any(DeviceShadow.class));
    }

    @Test
    @DisplayName("getShadow: throws NoSuchElementException when device does not exist for tenant")
    void getShadow_throwsNoSuchElementExceptionWhenDeviceNotFound() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shadowService.getShadow(deviceId, orgId))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Device not found");
    }

    // ── Update state & OCC tests ────────────────────────────────────────────

    @Test
    @DisplayName("updateShadow: updates desired state, increments version, and publishes retained delta")
    void updateShadow_updatesDesiredAndPublishesRetainedDelta() {
        when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.of(device));
        when(shadowRepository.findByDeviceIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.of(existingShadow));
        when(shadowRepository.save(any(DeviceShadow.class))).thenAnswer(inv -> inv.getArgument(0));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        Map<String, Object> desiredPatch = Map.of("tempThresholdAlert", 85.0);
        DeviceShadowUpdateRequest req = new DeviceShadowUpdateRequest(desiredPatch, null, 10L, "token-123");

        DeviceShadowDocument result = shadowService.updateShadow(deviceId, orgId, req);

        assertThat(result.version()).isEqualTo(11L);
        assertThat(result.state().desired()).containsEntry("tempThresholdAlert", 85.0);
        assertThat(result.state().delta()).containsEntry("tempThresholdAlert", 85.0);

        // Verify delta published to MQTT with retained = true
        String expectedDeltaTopic = String.format("tenants/%s/devices/%s/shadow/update/delta", orgId, deviceId);
        verify(mqttPublisher).publish(eq(expectedDeltaTopic), anyString(), eq(1), eq(true));
    }

    @Test
    @DisplayName("updateShadow: updates reported state and converges state (clears delta)")
    void updateShadow_clearsDeltaWhenReportedMatchesDesired() {
        when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.of(device));
        when(shadowRepository.findByDeviceIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.of(existingShadow));
        when(shadowRepository.save(any(DeviceShadow.class))).thenAnswer(inv -> inv.getArgument(0));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Report telemetryIntervalSec = 15 to match desired
        Map<String, Object> reportedPatch = Map.of("telemetryIntervalSec", 15);
        DeviceShadowUpdateRequest req = new DeviceShadowUpdateRequest(null, reportedPatch, 10L, "token-456");

        DeviceShadowDocument result = shadowService.updateShadow(deviceId, orgId, req);

        assertThat(result.version()).isEqualTo(11L);
        assertThat(result.state().reported()).containsEntry("telemetryIntervalSec", 15);
        assertThat(result.state().delta()).isEmpty(); // State converged!
    }

    @Test
    @DisplayName("updateShadow: throws DeviceShadowConflictException when expectedVersion does not match current version (OCC)")
    void updateShadow_throwsConflictWhenVersionMismatch_OCC() {
        when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.of(device));
        when(shadowRepository.findByDeviceIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.of(existingShadow));

        DeviceShadowUpdateRequest req = new DeviceShadowUpdateRequest(Map.of("valve", "OPEN"), null, 99L, "token-occ");

        assertThatThrownBy(() -> shadowService.updateShadow(deviceId, orgId, req))
                .isInstanceOf(DeviceShadowConflictException.class)
                .satisfies(ex -> {
                    DeviceShadowConflictException conflict = (DeviceShadowConflictException) ex;
                    assertThat(conflict.getCurrentVersion()).isEqualTo(10L);
                    assertThat(conflict.getMessage()).contains("Expected version 99, but current shadow version is 10");
                });

        verify(shadowRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateShadow: removes property when null value is provided in patch")
    void updateShadow_removesPropertyWhenNullPassed() {
        when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.of(device));
        when(shadowRepository.findByDeviceIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.of(existingShadow));
        when(shadowRepository.save(any(DeviceShadow.class))).thenAnswer(inv -> inv.getArgument(0));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        Map<String, Object> desiredPatch = new HashMap<>();
        desiredPatch.put("telemetryIntervalSec", null); // Remove property

        DeviceShadowUpdateRequest req = new DeviceShadowUpdateRequest(desiredPatch, null, null, null);
        DeviceShadowDocument result = shadowService.updateShadow(deviceId, orgId, req);

        assertThat(result.state().desired()).doesNotContainKey("telemetryIntervalSec");
    }

    @Test
    @DisplayName("deleteShadow: removes record from DB and clears Redis cache")
    void deleteShadow_deletesFromRepositoryAndRedis() {
        shadowService.deleteShadow(deviceId, orgId);

        verify(shadowRepository).deleteByDeviceIdAndOrganizationId(deviceId, orgId);
        verify(redisTemplate).delete("device:shadow:" + orgId + ":" + deviceId);
    }

    // ── MQTT shadow ingress handling tests ──────────────────────────────────

    @Test
    @DisplayName("handleMqttShadowMessage: processes update from edge device and publishes update/accepted")
    void handleMqttShadowMessage_processesReportedUpdateSuccessfully() {
        when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.of(device));
        when(shadowRepository.findByDeviceIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.of(existingShadow));
        when(shadowRepository.save(any(DeviceShadow.class))).thenAnswer(inv -> inv.getArgument(0));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String topic = String.format("tenants/%s/devices/%s/shadow/update", orgId, deviceId);
        String payload = """
                {
                  "state": {
                    "reported": {
                      "telemetryIntervalSec": 15
                    }
                  },
                  "version": 10,
                  "clientToken": "dev-token-99"
                }
                """;

        shadowService.handleMqttShadowMessage(topic, payload);

        String acceptedTopic = String.format("tenants/%s/devices/%s/shadow/update/accepted", orgId, deviceId);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttPublisher).publish(eq(acceptedTopic), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue()).contains("dev-token-99");
        assertThat(payloadCaptor.getValue()).contains("\"version\":11");
    }

    @Test
    @DisplayName("handleMqttShadowMessage: publishes update/rejected on OCC version conflict")
    void handleMqttShadowMessage_publishesRejectedOnOccConflict() {
        when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.of(device));
        when(shadowRepository.findByDeviceIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.of(existingShadow));

        String topic = String.format("tenants/%s/devices/%s/shadow/update", orgId, deviceId);
        String payload = """
                {
                  "state": {
                    "reported": {
                      "telemetryIntervalSec": 15
                    }
                  },
                  "version": 999,
                  "clientToken": "stale-client"
                }
                """;

        shadowService.handleMqttShadowMessage(topic, payload);

        String rejectedTopic = String.format("tenants/%s/devices/%s/shadow/update/rejected", orgId, deviceId);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttPublisher).publish(eq(rejectedTopic), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue()).contains("\"code\":409");
        assertThat(payloadCaptor.getValue()).contains("\"currentVersion\":10");
        assertThat(payloadCaptor.getValue()).contains("stale-client");
    }

    @Test
    @DisplayName("handleMqttShadowMessage: processes shadow get and publishes get/accepted")
    void handleMqttShadowMessage_handlesGetActionSuccessfully() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.of(device));
        when(shadowRepository.findByDeviceIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.of(existingShadow));

        String topic = String.format("tenants/%s/devices/%s/shadow/get", orgId, deviceId);
        shadowService.handleMqttShadowMessage(topic, "{}");

        String acceptedTopic = String.format("tenants/%s/devices/%s/shadow/get/accepted", orgId, deviceId);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttPublisher).publish(eq(acceptedTopic), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue()).contains("\"telemetryIntervalSec\":15");
        assertThat(payloadCaptor.getValue()).contains("\"version\":10");
    }
}
