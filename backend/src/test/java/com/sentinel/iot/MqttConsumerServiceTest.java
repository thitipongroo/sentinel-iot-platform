package com.sentinel.iot;

import com.sentinel.iot.kafka.KafkaTelemetryProducer;
import com.sentinel.iot.service.MqttConsumerService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MqttConsumerServiceTest {

    @Mock KafkaTelemetryProducer kafkaProducer;
    @Mock MessageChannel mqttDlqChannel;

    MqttConsumerService service;

    private static final String VALID_PAYLOAD =
            "{\"deviceId\":\"sensor-1\",\"temperature\":45.0,\"humidity\":60.0,\"motion\":false,\"smokePpm\":10.0}";

    @BeforeEach
    void setUp() {
        service = new MqttConsumerService(kafkaProducer, mqttDlqChannel, new SimpleMeterRegistry(), 200);
    }

    @Test
    void handleMessage_withValidPayload_forwardsToKafka() {
        service.handleMessage(message(VALID_PAYLOAD));

        verify(kafkaProducer).publish(eq("sensor-1"), eq(VALID_PAYLOAD));
        verify(mqttDlqChannel, never()).send(any(), anyLong());
    }

    @Test
    void handleMessage_withMalformedJson_routesToDlq() {
        service.handleMessage(message("not-valid-json{{{"));

        verify(mqttDlqChannel).send(argThat(m ->
                "PARSE_ERROR".equals(m.getHeaders().get("dlq-error-code"))), anyLong());
        verify(kafkaProducer, never()).publish(any(), any());
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
    void handleMessage_withMissingDeviceId_routesToDlq() {
        String payload = "{\"temperature\":45.0,\"humidity\":60.0}";

        service.handleMessage(message(payload));

        verify(mqttDlqChannel).send(argThat(m ->
                "VALIDATION_ERROR".equals(m.getHeaders().get("dlq-error-code"))), anyLong());
    }

    private Message<String> message(String payload) {
        return MessageBuilder.withPayload(payload).build();
    }
}
