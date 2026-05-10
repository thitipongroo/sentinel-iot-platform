package com.sentinel.iot;

import com.sentinel.iot.model.Device;
import com.sentinel.iot.repository.DeviceRepository;
import com.sentinel.iot.service.AlertService;
import com.sentinel.iot.service.MqttConsumerService;
import com.sentinel.iot.service.TelemetryService;
import com.sentinel.iot.websocket.TelemetryWebSocketHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MqttConsumerServiceTest {

    @Mock TelemetryService telemetryService;
    @Mock AlertService alertService;
    @Mock DeviceRepository deviceRepository;
    @Mock TelemetryWebSocketHandler webSocketHandler;
    @Mock MessageChannel mqttDlqChannel;

    MqttConsumerService service;

    private static final UUID DEVICE_ID = UUID.randomUUID();
    private static final String VALID_PAYLOAD =
            "{\"deviceId\":\"sensor-1\",\"temperature\":45.0,\"humidity\":60.0,\"motion\":false,\"smokePpm\":10.0}";

    @BeforeEach
    void setUp() {
        service = new MqttConsumerService(
                telemetryService, alertService, deviceRepository,
                webSocketHandler, mqttDlqChannel, new SimpleMeterRegistry());
    }

    @Test
    void handleMessage_withValidPayload_processesSuccessfully() {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setName("sensor-1");
        when(deviceRepository.findByName("sensor-1")).thenReturn(Optional.of(device));
        when(deviceRepository.save(any())).thenReturn(device);

        service.handleMessage(message(VALID_PAYLOAD));

        verify(telemetryService).save(eq(DEVICE_ID), eq(45.0), eq(60.0), eq(false), eq(10.0));
        verify(alertService).evaluate(eq(DEVICE_ID), eq("sensor-1"), eq(45.0), eq(60.0), eq(false), eq(10.0));
        verify(mqttDlqChannel, never()).send(any());
    }

    @Test
    void handleMessage_withMalformedJson_routesToDlq() {
        service.handleMessage(message("not-valid-json{{{"));

        verify(mqttDlqChannel).send(argThat(m ->
                "PARSE_ERROR".equals(m.getHeaders().get("dlq-error-code"))), anyLong());
        verify(telemetryService, never()).save(any(), any(), any(), any(), any());
    }

    @Test
    void handleMessage_withMissingTemperature_routesToDlq() {
        String payload = "{\"deviceId\":\"sensor-1\",\"humidity\":60.0}";

        service.handleMessage(message(payload));

        verify(mqttDlqChannel).send(argThat(m ->
                "VALIDATION_ERROR".equals(m.getHeaders().get("dlq-error-code"))), anyLong());
    }

    @Test
    void handleMessage_withTemperatureOutOfRange_routesToDlq() {
        String payload = "{\"deviceId\":\"sensor-1\",\"temperature\":999.0,\"humidity\":60.0}";

        service.handleMessage(message(payload));

        verify(mqttDlqChannel).send(argThat(m ->
                "VALIDATION_ERROR".equals(m.getHeaders().get("dlq-error-code"))), anyLong());
    }

    @Test
    void handleMessage_withNegativeHumidity_routesToDlq() {
        String payload = "{\"deviceId\":\"sensor-1\",\"temperature\":45.0,\"humidity\":-5.0}";

        service.handleMessage(message(payload));

        verify(mqttDlqChannel).send(argThat(m ->
                "VALIDATION_ERROR".equals(m.getHeaders().get("dlq-error-code"))), anyLong());
    }

    @Test
    void handleMessage_withUnknownDevice_routesToDlq() {
        when(deviceRepository.findByName("ghost-sensor")).thenReturn(Optional.empty());
        String payload = "{\"deviceId\":\"ghost-sensor\",\"temperature\":45.0,\"humidity\":60.0}";

        service.handleMessage(message(payload));

        verify(mqttDlqChannel).send(argThat(m ->
                "UNKNOWN_DEVICE".equals(m.getHeaders().get("dlq-error-code"))), anyLong());
    }

    @Test
    void handleMessage_whenTelemetryServiceThrows_routesToDlq() {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setName("sensor-1");
        when(deviceRepository.findByName("sensor-1")).thenReturn(Optional.of(device));
        when(deviceRepository.save(any())).thenReturn(device);
        doThrow(new RuntimeException("DB down")).when(telemetryService)
                .save(any(), any(), any(), any(), any());

        service.handleMessage(message(VALID_PAYLOAD));

        verify(mqttDlqChannel).send(argThat(m ->
                "PROCESSING_ERROR".equals(m.getHeaders().get("dlq-error-code"))), anyLong());
    }

    private Message<String> message(String payload) {
        return MessageBuilder.withPayload(payload).build();
    }
}
