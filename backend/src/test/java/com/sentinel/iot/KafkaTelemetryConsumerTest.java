package com.sentinel.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.kafka.KafkaTelemetryConsumer;
import com.sentinel.iot.model.Device;
import com.sentinel.iot.model.DeviceLifecycleStatus;
import com.sentinel.iot.repository.DeviceRepository;
import com.sentinel.iot.repository.TelemetryRepository;
import com.sentinel.iot.service.AlertService;
import com.sentinel.iot.service.RedisService;
import com.sentinel.iot.websocket.WebSocketBroadcastPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("KafkaTelemetryConsumer")
@ExtendWith(MockitoExtension.class)
class KafkaTelemetryConsumerTest {

    @Mock TelemetryRepository telemetryRepository;
    @Mock DeviceRepository deviceRepository;
    @Mock AlertService alertService;
    @Mock WebSocketBroadcastPublisher wsBroadcastPublisher;
    @Mock RedisService redisService;

    KafkaTelemetryConsumer consumer;

    private final UUID orgId    = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private final UUID deviceId = UUID.fromString("dddddddd-0000-0000-0000-000000000001");

    private static final String SENSOR_NAME = "sensor-1";
    private static final String V1_PAYLOAD =
            "{\"deviceId\":\"sensor-1\",\"temperature\":45.0,\"humidity\":60.0,\"smokePpm\":10.0}";

    @BeforeEach
    void setUp() {
        consumer = new KafkaTelemetryConsumer(
                telemetryRepository, deviceRepository, alertService,
                wsBroadcastPublisher, redisService,
                new ObjectMapper(), new SimpleMeterRegistry());
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Happy path — valid active device")
    class HappyPath {

        @SuppressWarnings("null")
        @Test
        @DisplayName("parses record and persists telemetry")
        void consumeBatch_parsesAndPersistsValidRecord() {
            Device device = activeDevice();
            when(deviceRepository.findAllByNameIn(anySet())).thenReturn(List.of(device));
            when(deviceRepository.saveAll(any())).thenReturn(List.of(device));

            consumer.consumeBatch(List.of(V1_PAYLOAD));

            verify(telemetryRepository).saveAll(argThat(list -> !((List<?>) list).isEmpty()));
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("transitions device to ONLINE and records lastSeen")
        void consumeBatch_setsDeviceOnline_andUpdatesLastSeen() {
            Device device = activeDevice();
            when(deviceRepository.findAllByNameIn(anySet())).thenReturn(List.of(device));
            when(deviceRepository.saveAll(any())).thenReturn(List.of(device));

            consumer.consumeBatch(List.of(V1_PAYLOAD));

            verify(deviceRepository).saveAll(argThat(devices ->
                    ((Iterable<Device>) devices).iterator().next().getStatus().equals("ONLINE")));
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("triggers alert evaluation after persisting telemetry")
        void consumeBatch_firesAlertEvaluation_afterSave() {
            Device device = activeDevice();
            when(deviceRepository.findAllByNameIn(anySet())).thenReturn(List.of(device));
            when(deviceRepository.saveAll(any())).thenReturn(List.of(device));

            consumer.consumeBatch(List.of(V1_PAYLOAD));

            verify(alertService).evaluate(eq(deviceId), eq(orgId), eq(SENSOR_NAME), any(), any());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("publishes raw payload to WebSocket broadcast channel")
        void consumeBatch_publishesToWebSocket_afterSave() {
            Device device = activeDevice();
            when(deviceRepository.findAllByNameIn(anySet())).thenReturn(List.of(device));
            when(deviceRepository.saveAll(any())).thenReturn(List.of(device));

            consumer.consumeBatch(List.of(V1_PAYLOAD));

            verify(wsBroadcastPublisher).publish(eq(orgId), eq(V1_PAYLOAD));
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("updates Redis latest-telemetry cache after save")
        void consumeBatch_updatesRedisCache_afterSave() {
            Device device = activeDevice();
            when(deviceRepository.findAllByNameIn(anySet())).thenReturn(List.of(device));
            when(deviceRepository.saveAll(any())).thenReturn(List.of(device));

            consumer.consumeBatch(List.of(V1_PAYLOAD));

            verify(redisService).setLatestTelemetry(eq(orgId), eq(deviceId.toString()), anyDouble(), anyDouble(), any(), any());
        }
    }

    // ── Drop conditions ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Drop conditions — record must be discarded")
    class DropConditions {

        @SuppressWarnings("null")
        @Test
        @DisplayName("drops unparseable JSON without calling saveAll")
        void consumeBatch_dropsUnparseableRecords() {
            consumer.consumeBatch(List.of("{not valid json at all!!!}"));

            verify(telemetryRepository, never()).saveAll(any());
            verify(alertService, never()).evaluate(any(), any(), any(), any(), any());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("drops record when device is not registered")
        void consumeBatch_dropsRecordForUnknownDevice() {
            when(deviceRepository.findAllByNameIn(anySet())).thenReturn(List.of());

            consumer.consumeBatch(List.of(V1_PAYLOAD));

            verify(telemetryRepository, never()).saveAll(any());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("drops record for INACTIVE device")
        void consumeBatch_dropsRecordForInactiveDevice() {
            Device device = activeDevice();
            device.setLifecycleStatus(DeviceLifecycleStatus.INACTIVE);
            when(deviceRepository.findAllByNameIn(anySet())).thenReturn(List.of(device));

            consumer.consumeBatch(List.of(V1_PAYLOAD));

            verify(telemetryRepository, never()).saveAll(any());
            verify(alertService, never()).evaluate(any(), any(), any(), any(), any());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("drops record for DECOMMISSIONED device")
        void consumeBatch_dropsRecordForDecommissionedDevice() {
            Device device = activeDevice();
            device.setLifecycleStatus(DeviceLifecycleStatus.DECOMMISSIONED);
            when(deviceRepository.findAllByNameIn(anySet())).thenReturn(List.of(device));

            consumer.consumeBatch(List.of(V1_PAYLOAD));

            verify(telemetryRepository, never()).saveAll(any());
        }
    }

    // ── Batch behaviour ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Batch behaviour")
    class BatchBehaviour {

        @SuppressWarnings("null")
        @Test
        @DisplayName("does nothing on empty batch")
        void consumeBatch_emptyBatch_doesNothing() {
            consumer.consumeBatch(List.of());

            verifyNoInteractions(telemetryRepository, alertService, wsBroadcastPublisher, redisService);
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("in a mixed batch saves only the valid records, discarding the rest")
        void consumeBatch_mixedBatch_onlySavesValidRecords() {
            Device device = activeDevice();
            when(deviceRepository.findAllByNameIn(anySet())).thenReturn(List.of(device));
            when(deviceRepository.saveAll(any())).thenReturn(List.of(device));

            consumer.consumeBatch(List.of("{bad json}", V1_PAYLOAD, "{also bad}"));

            verify(telemetryRepository).saveAll(argThat(list -> ((List<?>) list).size() == 1));
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("de-duplicates device saves when the same device appears multiple times in one batch")
        void consumeBatch_multipleSameDeviceInBatch_deduplicatesDeviceSave() {
            Device device = activeDevice();
            when(deviceRepository.findAllByNameIn(anySet())).thenReturn(List.of(device));
            when(deviceRepository.saveAll(any())).thenReturn(List.of(device));

            consumer.consumeBatch(List.of(V1_PAYLOAD, V1_PAYLOAD));

            verify(telemetryRepository).saveAll(argThat(list -> ((List<?>) list).size() == 2));
            verify(deviceRepository).saveAll(argThat(devices ->
                    ((java.util.Collection<?>) devices).size() == 1));
        }
    }

    // ── Multi-tenant collision safety ─────────────────────────────────────────

    @Nested
    @DisplayName("Multi-tenant collision safety — same device name across different tenants")
    class MultiTenantBatchResolution {

        @SuppressWarnings("null")
        @Test
        @DisplayName("resolves identical device names across different orgs when organizationId is provided")
        void consumeBatch_twoOrgsWithSameDeviceName_resolvesCorrectly() {
            UUID org1 = UUID.randomUUID();
            UUID org2 = UUID.randomUUID();
            UUID dev1 = UUID.randomUUID();
            UUID dev2 = UUID.randomUUID();

            Device d1 = new Device();
            d1.setId(dev1);
            d1.setName("sensor-shared");
            d1.setOrganizationId(org1);
            d1.setLifecycleStatus(DeviceLifecycleStatus.ACTIVE);

            Device d2 = new Device();
            d2.setId(dev2);
            d2.setName("sensor-shared");
            d2.setOrganizationId(org2);
            d2.setLifecycleStatus(DeviceLifecycleStatus.ACTIVE);

            when(deviceRepository.findAllByNameIn(anySet())).thenReturn(List.of(d1, d2));
            when(deviceRepository.saveAll(any())).thenReturn(List.of(d1, d2));

            String payloadOrg1 = "{\"deviceId\":\"sensor-shared\",\"organizationId\":\"" + org1 + "\",\"temperature\":22.0,\"humidity\":50.0}";
            String payloadOrg2 = "{\"deviceId\":\"sensor-shared\",\"organizationId\":\"" + org2 + "\",\"temperature\":28.0,\"humidity\":55.0}";

            consumer.consumeBatch(List.of(payloadOrg1, payloadOrg2));

            verify(telemetryRepository).saveAll(argThat(list -> ((List<?>) list).size() == 2));
            verify(wsBroadcastPublisher).publish(eq(org1), eq(payloadOrg1));
            verify(wsBroadcastPublisher).publish(eq(org2), eq(payloadOrg2));
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("safely drops ambiguous records when multiple orgs share device name and organizationId is missing")
        void consumeBatch_twoOrgsWithSameDeviceName_dropsAmbiguousWhenOrgIdMissing() {
            UUID org1 = UUID.randomUUID();
            UUID org2 = UUID.randomUUID();
            UUID dev1 = UUID.randomUUID();
            UUID dev2 = UUID.randomUUID();

            Device d1 = new Device();
            d1.setId(dev1);
            d1.setName("sensor-ambiguous");
            d1.setOrganizationId(org1);
            d1.setLifecycleStatus(DeviceLifecycleStatus.ACTIVE);

            Device d2 = new Device();
            d2.setId(dev2);
            d2.setName("sensor-ambiguous");
            d2.setOrganizationId(org2);
            d2.setLifecycleStatus(DeviceLifecycleStatus.ACTIVE);

            when(deviceRepository.findAllByNameIn(anySet())).thenReturn(List.of(d1, d2));

            String unScopedPayload = "{\"deviceId\":\"sensor-ambiguous\",\"temperature\":22.0,\"humidity\":50.0}";

            // Must NOT throw duplicate key IllegalStateException!
            consumer.consumeBatch(List.of(unScopedPayload));

            verify(telemetryRepository, never()).saveAll(any());
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Device activeDevice() {
        Device d = new Device();
        d.setId(deviceId);
        d.setName(SENSOR_NAME);
        d.setStatus("OFFLINE");
        d.setOrganizationId(orgId);
        d.setLifecycleStatus(DeviceLifecycleStatus.ACTIVE);
        return d;
    }
}
