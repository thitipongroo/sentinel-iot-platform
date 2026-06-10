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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

    // v1 JSON payload
    private static final String V1_PAYLOAD =
            "{\"deviceId\":\"sensor-1\",\"temperature\":45.0,\"humidity\":60.0,\"smokePpm\":10.0}";

    @BeforeEach
    void setUp() {
        consumer = new KafkaTelemetryConsumer(
                telemetryRepository, deviceRepository, alertService,
                wsBroadcastPublisher, redisService,
                new ObjectMapper(), new SimpleMeterRegistry());
    }

    private Device activeDevice() {
        Device d = new Device();
        d.setId(deviceId);
        d.setName(SENSOR_NAME);
        d.setStatus("OFFLINE");
        d.setOrganizationId(orgId);
        d.setLifecycleStatus(DeviceLifecycleStatus.ACTIVE);
        return d;
    }

    // ---- happy path --------------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void consumeBatch_parsesAndPersistsValidRecord() {
        Device device = activeDevice();
        when(deviceRepository.findAllByNameIn(anySet())).thenReturn(List.of(device));
        when(deviceRepository.saveAll(any())).thenReturn(List.of(device));

        consumer.consumeBatch(List.of(V1_PAYLOAD));

        verify(telemetryRepository).saveAll(argThat(list -> !((List<?>) list).isEmpty()));
    }

    @SuppressWarnings("null")
    @Test
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
    void consumeBatch_firesAlertEvaluation_afterSave() {
        Device device = activeDevice();
        when(deviceRepository.findAllByNameIn(anySet())).thenReturn(List.of(device));
        when(deviceRepository.saveAll(any())).thenReturn(List.of(device));

        consumer.consumeBatch(List.of(V1_PAYLOAD));

        verify(alertService).evaluate(eq(deviceId), eq(orgId), eq(SENSOR_NAME), any(), any());
    }

    @SuppressWarnings("null")
    @Test
    void consumeBatch_publishesToWebSocket_afterSave() {
        Device device = activeDevice();
        when(deviceRepository.findAllByNameIn(anySet())).thenReturn(List.of(device));
        when(deviceRepository.saveAll(any())).thenReturn(List.of(device));

        consumer.consumeBatch(List.of(V1_PAYLOAD));

        verify(wsBroadcastPublisher).publish(eq(orgId), eq(V1_PAYLOAD));
    }

    @SuppressWarnings("null")
    @Test
    void consumeBatch_updatesRedisCache_afterSave() {
        Device device = activeDevice();
        when(deviceRepository.findAllByNameIn(anySet())).thenReturn(List.of(device));
        when(deviceRepository.saveAll(any())).thenReturn(List.of(device));

        consumer.consumeBatch(List.of(V1_PAYLOAD));

        verify(redisService).setLatestTelemetry(eq(deviceId.toString()), any(), any(), any(), any());
    }

    // ---- drop conditions ---------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void consumeBatch_dropsUnparseableRecords_doesNotCallSaveAll() {
        consumer.consumeBatch(List.of("{not valid json at all!!!}"));

        verify(telemetryRepository, never()).saveAll(any());
        verify(alertService, never()).evaluate(any(), any(), any(), any(), any());
    }

    @SuppressWarnings("null")
    @Test
    void consumeBatch_dropsRecordForUnknownDevice() {
        when(deviceRepository.findAllByNameIn(anySet())).thenReturn(List.of()); // no device found

        consumer.consumeBatch(List.of(V1_PAYLOAD));

        verify(telemetryRepository, never()).saveAll(any());
    }

    @SuppressWarnings("null")
    @Test
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
    void consumeBatch_dropsRecordForDecommissionedDevice() {
        Device device = activeDevice();
        device.setLifecycleStatus(DeviceLifecycleStatus.DECOMMISSIONED);
        when(deviceRepository.findAllByNameIn(anySet())).thenReturn(List.of(device));

        consumer.consumeBatch(List.of(V1_PAYLOAD));

        verify(telemetryRepository, never()).saveAll(any());
    }

    // ---- batch behaviour ---------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void consumeBatch_emptyBatch_doesNothing() {
        consumer.consumeBatch(List.of());

        verifyNoInteractions(telemetryRepository, alertService, wsBroadcastPublisher, redisService);
    }

    @SuppressWarnings("null")
    @Test
    void consumeBatch_mixedBatch_onlySavesValidRecords() {
        Device device = activeDevice();
        when(deviceRepository.findAllByNameIn(anySet())).thenReturn(List.of(device));
        when(deviceRepository.saveAll(any())).thenReturn(List.of(device));

        consumer.consumeBatch(List.of(
                "{bad json}",
                V1_PAYLOAD,
                "{also bad}"
        ));

        // Only 1 valid record persisted
        verify(telemetryRepository).saveAll(argThat(list -> ((List<?>) list).size() == 1));
    }

    @SuppressWarnings("null")
    @Test
    void consumeBatch_multipleSameDeviceInBatch_oneDeviceSave() {
        Device device = activeDevice();
        when(deviceRepository.findAllByNameIn(anySet())).thenReturn(List.of(device));
        when(deviceRepository.saveAll(any())).thenReturn(List.of(device));

        // Two valid records for the same device — both should be persisted
        consumer.consumeBatch(List.of(V1_PAYLOAD, V1_PAYLOAD));

        verify(telemetryRepository).saveAll(argThat(list -> ((List<?>) list).size() == 2));
        // Device deduplication: only saved once (LinkedHashMap by device ID)
        verify(deviceRepository).saveAll(argThat(devices ->
                ((java.util.Collection<?>) devices).size() == 1));
    }
}
