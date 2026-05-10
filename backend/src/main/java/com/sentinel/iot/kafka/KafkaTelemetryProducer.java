package com.sentinel.iot.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaTelemetryProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.topics.telemetry-raw:telemetry.raw}")
    private String topic;

    /**
     * Publishes a raw telemetry JSON payload to the telemetry.raw topic.
     * The device name is used as the partition key so all events from one device
     * land on the same partition, preserving per-device ordering.
     */
    public void publish(String deviceName, String rawPayload) {
        kafkaTemplate.send(topic, deviceName, rawPayload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka publish failed for device={}: {}", deviceName, ex.getMessage());
                    }
                });
    }
}
