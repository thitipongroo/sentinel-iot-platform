package com.sentinel.iot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.dto.TelemetryMessage;
import com.sentinel.iot.kafka.KafkaTelemetryProducer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * MQTT ingress gateway: parse → validate → publish to Kafka.
 * No DB calls on this path — device resolution, persistence, alerts, and WebSocket
 * broadcast are all handled downstream by KafkaTelemetryConsumer.
 */
@Service
@Slf4j
public class MqttConsumerService {

    private static final double TEMP_MIN  = -40.0;
    private static final double TEMP_MAX  = 200.0;
    private static final double HUM_MIN   = 0.0;
    private static final double HUM_MAX   = 100.0;
    private static final double SMOKE_MIN = 0.0;

    private final KafkaTelemetryProducer kafkaProducer;
    private final MessageChannel mqttDlqChannel;
    private final ObjectMapper objectMapper;
    private final Counter mqttCounter;
    private final Counter dlqCounter;

    public MqttConsumerService(KafkaTelemetryProducer kafkaProducer,
                               @Qualifier("mqttDlqChannel") MessageChannel mqttDlqChannel,
                               MeterRegistry meterRegistry) {
        this.kafkaProducer  = kafkaProducer;
        this.mqttDlqChannel = mqttDlqChannel;
        this.objectMapper   = new ObjectMapper();
        this.mqttCounter    = Counter.builder("sentinel.mqtt.messages")
                .description("MQTT messages that passed parse+validate and were forwarded to Kafka")
                .register(meterRegistry);
        this.dlqCounter     = Counter.builder("sentinel.mqtt.dlq")
                .description("MQTT messages routed to dead-letter queue (parse or validation failure)")
                .register(meterRegistry);
    }

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMessage(Message<?> message) {
        String rawPayload = message.getPayload().toString();

        // ── 1. Parse ─────────────────────────────────────────────────────────────
        TelemetryMessage msg;
        try {
            msg = objectMapper.readValue(rawPayload, TelemetryMessage.class);
        } catch (Exception e) {
            log.warn("DLQ: malformed JSON payload — {}", e.getMessage());
            sendToDlq(rawPayload, "PARSE_ERROR", e.getMessage());
            return;
        }

        // ── 2. Validate ───────────────────────────────────────────────────────────
        String validationError = validate(msg);
        if (validationError != null) {
            log.warn("DLQ: validation failed for device={} — {}", msg.getDeviceId(), validationError);
            sendToDlq(rawPayload, "VALIDATION_ERROR", validationError);
            return;
        }

        // ── 3. Forward to Kafka ───────────────────────────────────────────────────
        // Device name as partition key → all events from one device land on the same
        // partition, preserving per-device ordering in the consumer.
        kafkaProducer.publish(msg.getDeviceId(), rawPayload);
        mqttCounter.increment();

        log.debug("Forwarded telemetry from device={} to Kafka", msg.getDeviceId());
    }

    private String validate(TelemetryMessage msg) {
        if (msg.getDeviceId() == null || msg.getDeviceId().isBlank()) {
            return "deviceId is required";
        }
        if (msg.getTemperature() == null) {
            return "temperature is required";
        }
        if (msg.getTemperature() < TEMP_MIN || msg.getTemperature() > TEMP_MAX) {
            return String.format("temperature %.1f out of range [%.0f, %.0f]",
                    msg.getTemperature(), TEMP_MIN, TEMP_MAX);
        }
        if (msg.getHumidity() == null) {
            return "humidity is required";
        }
        if (msg.getHumidity() < HUM_MIN || msg.getHumidity() > HUM_MAX) {
            return String.format("humidity %.1f out of range [%.0f, %.0f]",
                    msg.getHumidity(), HUM_MIN, HUM_MAX);
        }
        if (msg.getSmokePpm() != null && msg.getSmokePpm() < SMOKE_MIN) {
            return String.format("smokePpm %.1f cannot be negative", msg.getSmokePpm());
        }
        return null;
    }

    private void sendToDlq(String rawPayload, String errorCode, String errorDetail) {
        try {
            dlqCounter.increment();
            Message<String> dlqMessage = MessageBuilder
                    .withPayload(rawPayload)
                    .setHeader("dlq-error-code", errorCode)
                    .setHeader("dlq-error-detail", errorDetail)
                    .setHeader("dlq-timestamp", Instant.now().toString())
                    .build();
            mqttDlqChannel.send(dlqMessage, 3000);
        } catch (Exception ex) {
            log.error("Failed to publish to DLQ (errorCode={}): {}", errorCode, ex.getMessage());
        }
    }
}
