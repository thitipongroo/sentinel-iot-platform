package com.sentinel.iot.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
public class KafkaConfig {

    @Value("${kafka.topics.telemetry-raw:telemetry.raw}")
    private String telemetryRawTopic;

    @Bean
    public NewTopic telemetryRawTopic() {
        return TopicBuilder.name(telemetryRawTopic)
                .partitions(3)   // allows up to 3 parallel consumer instances in the group
                .replicas(1)     // single-broker dev; bump to 3 in production
                .build();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> batchKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);
        // Commit offsets only after the entire batch is processed successfully.
        // If saveAll() throws, the offset is NOT committed → Kafka redelivers the batch.
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        return factory;
    }
}
