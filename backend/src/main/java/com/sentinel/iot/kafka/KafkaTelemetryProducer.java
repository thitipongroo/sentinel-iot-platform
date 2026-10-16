package com.sentinel.iot.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class KafkaTelemetryProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaTelemetryProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Value("${kafka.topics.telemetry-raw:telemetry.raw}")
    private String topic;

    /**
     * Publishes a raw telemetry JSON payload to the telemetry.raw topic.
     * The device name or composite key is used as the partition key so all events from one device
     * land on the same partition, preserving per-device ordering.
     */
    @SuppressWarnings("null")
    public void publish(String partitionKey, String rawPayload) {
        try {
            kafkaTemplate.send(topic, partitionKey, rawPayload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Kafka publish failed for key={}: {}", partitionKey, ex.getMessage());
                        }
                    });
        } catch (Exception ex) {
            log.error("Kafka publish failed for key={}: {}", partitionKey, ex.getMessage());
        }
    }

    /**
     * Publishes a raw telemetry JSON payload with multi-tenant partition key and header.
     * Partition key is formatted as <orgId>:<deviceName> to prevent partition-level collisions.
     */
    @SuppressWarnings("null")
    public void publish(java.util.UUID orgId, String deviceName, String rawPayload) {
        String partitionKey = (orgId != null ? orgId + ":" : "") + deviceName;
        org.apache.kafka.clients.producer.ProducerRecord<String, String> record =
                new org.apache.kafka.clients.producer.ProducerRecord<>(topic, partitionKey, rawPayload);
        if (orgId != null) {
            record.headers().add("tenant-id", orgId.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        try {
            kafkaTemplate.send(record)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Kafka publish failed for orgId={} device={}: {}", orgId, deviceName, ex.getMessage());
                        }
                    });
        } catch (Exception ex) {
            log.error("Kafka publish failed for orgId={} device={}: {}", orgId, deviceName, ex.getMessage());
        }
    }
}
