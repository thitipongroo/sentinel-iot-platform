package com.sentinel.iot;

import com.sentinel.iot.kafka.KafkaTelemetryProducer;
import com.sentinel.iot.service.MqttConsumerService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("MqttConsumerService")
@ExtendWith(MockitoExtension.class)
class MqttConsumerServiceTest {

    @Mock KafkaTelemetryProducer kafkaProducer;
    @Mock MessageChannel mqttDlqChannel;

    MqttConsumerService service;

    private static final String VALID_PAYLOAD =
            "{\"deviceId\":\"sensor-1\",\"temperature\":45.0,\"humidity\":60.0," +
            "\"motion\":false,\"smokePpm\":10.0}";

    @BeforeEach
    void setUp() {
        service = new MqttConsumerService(kafkaProducer, mqttDlqChannel, new SimpleMeterRegistry(), 200);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @SuppressWarnings("null")
        @Test
        @DisplayName("forwards valid payload to Kafka and does not touch DLQ")
        void handleMessage_withValidPayload_forwardsToKafka() {
            service.handleMessage(message(VALID_PAYLOAD));

            verify(kafkaProducer).publish(eq("sensor-1"), eq(VALID_PAYLOAD));
            verify(mqttDlqChannel, never()).send(any(), anyLong());
        }
    }

    // ── DLQ routing for malformed payloads ────────────────────────────────────

    @Nested
    @DisplayName("DLQ routing — malformed input")
    class DlqRouting {

        @SuppressWarnings("null")
        @Test
        @DisplayName("routes malformed JSON to DLQ with PARSE_ERROR code")
        void handleMessage_withMalformedJson_routesToDlq() {
            service.handleMessage(message("not-valid-json{{{"));

            verify(mqttDlqChannel).send(argThat(m ->
                    "PARSE_ERROR".equals(m.getHeaders().get("dlq-error-code"))), anyLong());
            verify(kafkaProducer, never()).publish(any(), any());
        }

        @SuppressWarnings("null")
        @ParameterizedTest(name = "payload: {0}")
        @ValueSource(strings = {
            "{\"deviceId\":\"sensor-1\",\"humidity\":60.0}",            // missing temperature
            "{\"temperature\":45.0,\"humidity\":60.0}",                  // missing deviceId
            "{\"deviceId\":\"sensor-1\",\"temperature\":999.0,\"humidity\":60.0}",   // temp out of range
            "{\"deviceId\":\"sensor-1\",\"temperature\":45.0,\"humidity\":-5.0}"    // humidity out of range
        })
        @DisplayName("routes semantically invalid payload to DLQ with VALIDATION_ERROR code")
        void handleMessage_withInvalidPayload_routesToDlq(String payload) {
            service.handleMessage(message(payload));

            verify(mqttDlqChannel).send(argThat(m ->
                    "VALIDATION_ERROR".equals(m.getHeaders().get("dlq-error-code"))), anyLong());
            verify(kafkaProducer, never()).publish(any(), any());
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("null")
    private Message<String> message(String payload) {
        return MessageBuilder.withPayload(payload).build();
    }
}
