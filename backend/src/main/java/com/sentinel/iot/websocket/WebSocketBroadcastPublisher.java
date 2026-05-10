package com.sentinel.iot.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes a telemetry payload to the Redis pub/sub broadcast channel.
 *
 * All backend instances subscribe to this channel via WebSocketBroadcastSubscriber.
 * Each subscriber fans the message out to its own local WebSocket sessions.
 * This decouples "who processed the Kafka batch" from "which instance holds the
 * WebSocket session" — any instance can trigger a cluster-wide broadcast.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketBroadcastPublisher {

    private final StringRedisTemplate redisTemplate;

    @Value("${ws.broadcast.channel:ws:telemetry}")
    private String channel;

    public void publish(String message) {
        try {
            redisTemplate.convertAndSend(channel, message);
        } catch (Exception e) {
            // Non-fatal: Redis pub/sub failure means live dashboard misses this event,
            // but telemetry is already persisted to PostgreSQL via Kafka consumer.
            log.warn("Redis WebSocket broadcast failed — dashboard may miss event: {}", e.getMessage());
        }
    }
}
