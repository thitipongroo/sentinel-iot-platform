package com.sentinel.iot.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;

@Configuration
public class KafkaConfig {

    @Value("${kafka.topics.telemetry-raw:telemetry.raw}")
    private String telemetryRawTopic;

    @Value("${kafka.topics.telemetry-dlq:telemetry.dlq}")
    private String telemetryDlqTopic;

    // ── Topic declarations ────────────────────────────────────────────────────

    @SuppressWarnings("null")
@Bean
    public NewTopic telemetryRawTopic() {
        return TopicBuilder.name(telemetryRawTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @SuppressWarnings("null")
@Bean
    public NewTopic telemetryDlqTopic() {
        return TopicBuilder.name(telemetryDlqTopic)
                .partitions(3)
                .replicas(1)
                // 7-day retention: DLQ is the durable fallback when DB is unavailable.
                // Records survive Redis restarts, memory pressure, and short DB outages.
                .config(TopicConfig.RETENTION_MS_CONFIG,
                        String.valueOf(Duration.ofDays(7).toMillis()))
                .build();
    }

    // ── Error handler: retry → DLQ ────────────────────────────────────────────

    /**
     * On batch processing failure (e.g. DB unavailable), retries the batch 3 times
     * with a 1s fixed delay. After exhausting retries, each record in the failed batch
     * is published individually to telemetry.dlq — the durable fallback log.
     *
     * Partition assignment: DLQ records land on the same partition number as the
     * original record (mod 3) to preserve per-device ordering in the DLQ.
     */
    @SuppressWarnings("null")
@Bean
    public DefaultErrorHandler kafkaBatchErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(telemetryDlqTopic, record.partition() % 3));

        // 3 retries × 1 s → total ~3 s before routing to DLQ.
        // Keep short: the Kafka consumer is the hot path; extended retries here
        // block the entire partition. Durability is guaranteed by DLQ retention.
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 3L));
    }

    // ── Consumer container factories ──────────────────────────────────────────

    /**
     * Batch consumer factory for telemetry.raw.
     * AckMode.BATCH commits the offset only after saveAll() returns successfully.
     * On failure, DefaultErrorHandler retries then routes to DLQ.
     */
    @SuppressWarnings("null")
@Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> batchKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler kafkaBatchErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        factory.setCommonErrorHandler(kafkaBatchErrorHandler);
        return factory;
    }

    /**
     * Single-record consumer factory for telemetry.dlq.
     * AckMode.RECORD commits per-record so a single bad record doesn't block the rest.
     * No DeadLetterPublishingRecoverer here — if the DLQ consumer fails, Kafka redelivers
     * with exponential backoff until DB recovers. 7-day topic retention is the safety net.
     */
    @SuppressWarnings("null")
@Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> dlqKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        // Exponential backoff with no DLQ recoverer: failures keep retrying until DB is back.
        // Max interval capped at 5 min so recovery isn't delayed too long after DB comes up.
        ExponentialBackOff dlqBackOff = new ExponentialBackOff(5_000L, 2.0);
        dlqBackOff.setMaxInterval(300_000L);
        factory.setCommonErrorHandler(new DefaultErrorHandler(dlqBackOff));
        return factory;
    }
}
