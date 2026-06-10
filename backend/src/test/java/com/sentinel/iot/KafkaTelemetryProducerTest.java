package com.sentinel.iot;

import com.sentinel.iot.kafka.KafkaTelemetryProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@DisplayName("KafkaTelemetryProducer")
@ExtendWith(MockitoExtension.class)
class KafkaTelemetryProducerTest {

    @Mock
    KafkaTemplate<String, String> kafkaTemplate;

    KafkaTelemetryProducer producer;

    @BeforeEach
    void setUp() {
        producer = new KafkaTelemetryProducer(kafkaTemplate);
        ReflectionTestUtils.setField(producer, "topic", "telemetry.raw");
    }

    @Nested
    @DisplayName("publish")
    class Publish {

        @SuppressWarnings("null")
        @Test
        @DisplayName("delegates to KafkaTemplate.send() with the configured topic, device key, and raw payload")
        void publish_delegatesToKafkaTemplate() {
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            producer.publish("device-01", "{\"temp\":22.5}");

            verify(kafkaTemplate).send("telemetry.raw", "device-01", "{\"temp\":22.5}");
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("does not propagate the exception when KafkaTemplate.send() fails")
        void publish_kafkaFailure_doesNotThrow() {
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

            assertThatNoException().isThrownBy(() -> producer.publish("device-01", "{\"temp\":22.5}"));
        }
    }
}
