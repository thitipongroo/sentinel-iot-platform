package com.sentinel.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.kafka.TelemetryDlqConsumer;
import com.sentinel.iot.model.Device;
import com.sentinel.iot.model.DeviceLifecycleStatus;
import com.sentinel.iot.repository.DeviceRepository;
import com.sentinel.iot.repository.TelemetryRepository;
import com.sentinel.iot.service.RedisService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryDlqConsumerTest {

    @Mock TelemetryRepository telemetryRepository;
    @Mock DeviceRepository deviceRepository;
    @Mock RedisService redisService;

    TelemetryDlqConsumer consumer;

    private final UUID orgId    = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private final UUID deviceId = UUID.fromString("dddddddd-0000-0000-0000-000000000001");

    private static final String VALID_PAYLOAD =
            "{\"deviceId\":\"sensor-1\",\"temperature\":38.5,\"humidity\":55.0}";

    @BeforeEach
    void setUp() {
        consumer = new TelemetryDlqConsumer(
                telemetryRepository, deviceRepository, redisService,
                new ObjectMapper(), new SimpleMeterRegistry());
    }

    private Device activeDevice() {
        Device d = new Device();
        d.setId(deviceId);
        d.setName("sensor-1");
        d.setOrganizationId(orgId);
        d.setLifecycleStatus(DeviceLifecycleStatus.ACTIVE);
        return d;
    }

    // ---- happy path --------------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void processDlq_savesRecord_andUpdatesRedis() {
        when(deviceRepository.findByName("sensor-1")).thenReturn(Optional.of(activeDevice()));

        consumer.processDlq(VALID_PAYLOAD);

        verify(telemetryRepository).save(any());
        verify(redisService).setLatestTelemetry(eq(deviceId.toString()), any(), any(), any(), any());
    }

    @SuppressWarnings("null")
    @Test
    void processDlq_redisFails_doesNotThrow() {
        when(deviceRepository.findByName("sensor-1")).thenReturn(Optional.of(activeDevice()));
        doThrow(new RuntimeException("Redis down")).when(redisService).setLatestTelemetry(anyString(), any(), any(), any(), any());

        assertThatNoException().isThrownBy(() -> consumer.processDlq(VALID_PAYLOAD));
        verify(telemetryRepository).save(any()); // DB write still happened
    }

    // ---- discard conditions ------------------------------------------------

    @Test
    void processDlq_discardsUnparseablePayload_noDB() {
        consumer.processDlq("{this is not valid JSON!!!");

        verifyNoInteractions(telemetryRepository, deviceRepository, redisService);
    }

    @SuppressWarnings("null")
    @Test
    void processDlq_discardsUnknownDevice_noDB() {
        when(deviceRepository.findByName("sensor-1")).thenReturn(Optional.empty());

        consumer.processDlq(VALID_PAYLOAD);

        verify(telemetryRepository, never()).save(any());
    }

    @SuppressWarnings("null")
    @Test
    void processDlq_discardsInactiveDevice_noDB() {
        Device device = activeDevice();
        device.setLifecycleStatus(DeviceLifecycleStatus.INACTIVE);
        when(deviceRepository.findByName("sensor-1")).thenReturn(Optional.of(device));

        consumer.processDlq(VALID_PAYLOAD);

        verify(telemetryRepository, never()).save(any());
    }

    @SuppressWarnings("null")
    @Test
    void processDlq_discardsDecommissionedDevice_noDB() {
        Device device = activeDevice();
        device.setLifecycleStatus(DeviceLifecycleStatus.DECOMMISSIONED);
        when(deviceRepository.findByName("sensor-1")).thenReturn(Optional.of(device));

        consumer.processDlq(VALID_PAYLOAD);

        verify(telemetryRepository, never()).save(any());
    }

    // ---- offset commit behaviour (DB failure retries) ----------------------

    @SuppressWarnings("null")
    @Test
    void processDlq_dbFailure_propagatesException_forOffsetRetry() {
        when(deviceRepository.findByName("sensor-1")).thenReturn(Optional.of(activeDevice()));
        doThrow(new RuntimeException("PostgreSQL unavailable")).when(telemetryRepository).save(any());

        // Exception must propagate so Kafka withholds the offset commit
        assertThatThrownBy(() -> consumer.processDlq(VALID_PAYLOAD))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("PostgreSQL unavailable");
    }
}
