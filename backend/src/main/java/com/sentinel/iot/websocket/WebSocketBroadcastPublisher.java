package com.sentinel.iot.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Publishes a telemetry payload to the Redis pub/sub broadcast channel.
 *
 * All backend instances subscribe to this channel via WebSocketBroadcastSubscriber.
 * Each subscriber fans the message out to its own local WebSocket sessions for the
 * matching tenant — preventing cross-tenant data leakage via the live dashboard.
 *
 * Message format: {@code <orgId>|<rawPayload>}
 * The handler strips the prefix before forwarding to the client.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketBroadcastPublisher {

    private final StringRedisTemplate redisTemplate;

    @Value("${ws.broadcast.channel:ws:telemetry}")
    private String channel;

    public void publish(UUID organizationId, String rawPayload) {
        try {
            String envelope = organizationId + "|" + rawPayload;
            redisTemplate.convertAndSend(channel, envelope);
        } catch (Exception e) {
            // Non-fatal: Redis pub/sub failure means live dashboard misses this event,
            // but telemetry is already persisted to PostgreSQL via Kafka consumer.
            log.warn("Redis WebSocket broadcast failed — dashboard may miss event: {}", e.getMessage());
        }
    }
}
